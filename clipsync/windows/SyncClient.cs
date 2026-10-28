using System.Net.NetworkInformation;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;

namespace ClipSync;

public enum SyncStatus { NotSetUp, Connecting, Connected, Offline, Error }

/// <summary>
/// Listens for clips from the other devices and sends ours. Same message format as the
/// Android app (Sender.kt / Incoming.kt), so phones and PCs share one encrypted channel.
/// </summary>
public sealed class SyncClient
{
    const int MaxInline = 3800; // ntfy.sh keeps messages under 4096 bytes inline
    static readonly TimeSpan Fresh = TimeSpan.FromMinutes(10);
    static readonly JsonSerializerOptions Json = new() { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    readonly Settings _settings;
    readonly SynchronizationContext _ui;
    CancellationTokenSource? _cts;

    public Crypto? Crypto { get; private set; }
    public SyncStatus Status { get; private set; } = SyncStatus.NotSetUp;

    /// <summary>Raised on the UI thread.</summary>
    public event Action<SyncStatus>? StatusChanged;

    /// <summary>Raised on the UI thread: text, sender name, and whether it's fresh (not a replay).</summary>
    public event Action<string, string, bool>? TextReceived;

    public SyncClient(Settings settings, SynchronizationContext ui)
    {
        _settings = settings;
        _ui = ui;
    }

    public void Start()
    {
        Stop();
        if (string.IsNullOrEmpty(_settings.Code))
        {
            SetStatus(SyncStatus.NotSetUp);
            return;
        }
        Crypto = new Crypto(_settings.Code);
        _cts = new CancellationTokenSource();
        var crypto = Crypto;
        var token = _cts.Token;
        SetStatus(SyncStatus.Connecting);
        Task.Run(() => Listen(crypto, token));
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts = null;
    }

    public async Task SendText(string text)
    {
        var crypto = Crypto ?? throw new InvalidOperationException("Set up ClipSync first");
        var inline = crypto.EncryptString(Header("text", text));
        if (inline.Length <= MaxInline)
        {
            await Relay.PublishMessage(crypto.Topic, inline);
        }
        else
        {
            await Relay.PublishAttachment(crypto.Topic, crypto.EncryptString(Header("text", null)),
                crypto.Encrypt(Encoding.UTF8.GetBytes(text)));
        }
        _settings.AddHistory(true, text, _settings.DeviceName, DateTimeOffset.Now);
    }

    /// <summary>Tells the other devices our name; they answer with an ack carrying theirs.</summary>
    public async Task SendHello(bool ack)
    {
        var crypto = Crypto;
        if (crypto == null) return;
        await Relay.PublishMessage(crypto.Topic, crypto.EncryptString(Header(ack ? "hello_ack" : "hello", null)));
    }

    string Header(string type, string? text)
    {
        var header = new Dictionary<string, object>
        {
            ["v"] = 1,
            ["from"] = _settings.DeviceId,
            ["name"] = _settings.DeviceName,
            ["type"] = type,
        };
        if (text != null) header["text"] = text;
        return JsonSerializer.Serialize(header, Json);
    }

    async Task Listen(Crypto crypto, CancellationToken ct)
    {
        var backoff = 2000;
        var failures = 0;
        var greeted = false;
        while (!ct.IsCancellationRequested)
        {
            try
            {
                _settings.LastMessageIds.TryGetValue(crypto.Topic, out var since);
                // Drop the connection if nothing (not even a keepalive, every 45s) arrives for 90s.
                using var watchdog = CancellationTokenSource.CreateLinkedTokenSource(ct);
                watchdog.CancelAfter(TimeSpan.FromSeconds(90));
                using var res = await Relay.Subscribe(crypto.Topic, since, watchdog.Token);
                using var reader = new StreamReader(await res.Content.ReadAsStreamAsync(watchdog.Token));
                while (!ct.IsCancellationRequested)
                {
                    watchdog.CancelAfter(TimeSpan.FromSeconds(90));
                    var line = await reader.ReadLineAsync(watchdog.Token);
                    if (line == null) break;
                    if (line.Length == 0) continue;

                    using var doc = JsonDocument.Parse(line);
                    var ev = doc.RootElement;
                    switch (Str(ev, "event"))
                    {
                        case "open":
                            backoff = 2000;
                            failures = 0;
                            SetStatus(SyncStatus.Connected);
                            // Introduce ourselves until another device has answered.
                            if (!greeted && _settings.Devices.Count == 0)
                            {
                                greeted = true;
                                _ = SendHello(false).ContinueWith(_ => { });
                            }
                            break;
                        case "message":
                            try
                            {
                                await Handle(crypto, ev);
                            }
                            catch (Exception e)
                            {
                                System.Diagnostics.Debug.WriteLine("Couldn't handle clip: " + e);
                            }
                            _settings.LastMessageIds[crypto.Topic] = Str(ev, "id");
                            _settings.Save();
                            break;
                    }
                }
            }
            catch (Exception) when (ct.IsCancellationRequested)
            {
                return;
            }
            catch (Exception e)
            {
                System.Diagnostics.Debug.WriteLine("Connection dropped: " + e.Message);
            }
            if (ct.IsCancellationRequested) return;

            failures++;
            SetStatus(!NetworkInterface.GetIsNetworkAvailable() ? SyncStatus.Offline
                : failures >= 3 ? SyncStatus.Error : SyncStatus.Connecting);
            try
            {
                await Task.Delay(backoff, ct);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            backoff = Math.Min(backoff * 2, 60_000);
        }
    }

    async Task Handle(Crypto crypto, JsonElement ev)
    {
        JsonDocument headerDoc;
        try
        {
            headerDoc = JsonDocument.Parse(crypto.DecryptString(Str(ev, "message")));
        }
        catch
        {
            return; // not encrypted with our code
        }
        using (headerDoc)
        {
            var h = headerDoc.RootElement;
            var from = Str(h, "from");
            if (from == _settings.DeviceId) return;

            var name = Str(h, "name");
            if (name != "" && (!_settings.Devices.TryGetValue(from, out var known) || known != name))
            {
                _settings.Devices[from] = name;
                _settings.Save();
            }

            switch (Str(h, "type"))
            {
                case "hello":
                    _ = SendHello(true).ContinueWith(_ => { });
                    return;
                case "text":
                    string text;
                    if (h.TryGetProperty("text", out var t))
                    {
                        text = t.GetString() ?? "";
                    }
                    else
                    {
                        if (!ev.TryGetProperty("attachment", out var att)) return;
                        text = Encoding.UTF8.GetString(crypto.Decrypt(await Relay.Download(Str(att, "url"))));
                    }
                    var sentAt = ev.TryGetProperty("time", out var tm) ? DateTimeOffset.FromUnixTimeSeconds(tm.GetInt64()) : DateTimeOffset.Now;
                    var sender = name == "" ? "your phone" : name;
                    _settings.AddHistory(false, text, sender, sentAt);
                    var fresh = DateTimeOffset.Now - sentAt < Fresh;
                    _ui.Post(_ => TextReceived?.Invoke(text, sender, fresh), null);
                    return;
            }
        }
    }

    void SetStatus(SyncStatus status)
    {
        if (Status == status) return;
        Status = status;
        _ui.Post(_ => StatusChanged?.Invoke(status), null);
    }

    static string Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";
}
