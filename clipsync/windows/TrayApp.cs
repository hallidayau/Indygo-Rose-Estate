using System.Diagnostics;
using System.Drawing.Imaging;
using System.Net.NetworkInformation;
using System.Reflection;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace ClipSync;

/// <summary>The tray icon, its menu, and the glue between the clipboard and the relay.</summary>
sealed class TrayApp : ApplicationContext
{
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    readonly Settings _settings = Settings.Load();
    readonly SynchronizationContext _ui = SynchronizationContext.Current!;
    readonly SyncClient _sync;
    readonly NotifyIcon _tray;
    readonly ClipboardWatcher _watcher;
    readonly Icon _icon;
    readonly Icon _iconDimmed;

    readonly ToolStripMenuItem _statusItem = new() { Enabled = false };
    readonly ToolStripMenuItem _sendItem = new("Send clipboard now");
    readonly ToolStripMenuItem _recentMenu = new("Recent");
    readonly ToolStripMenuItem _autoSendItem = new("Automatically send what I copy") { CheckOnClick = true };
    readonly ToolStripMenuItem _notifyItem = new("Show notifications") { CheckOnClick = true };
    readonly ToolStripMenuItem _startupItem = new("Start with Windows") { CheckOnClick = true };
    readonly ToolStripMenuItem _pairingItem = new("Pairing && devices…");

    // The last text we sent or received, so our own clipboard writes and repeat
    // notifications for the same copy aren't sent back out.
    string? _lastSynced;
    string? _balloonLink;

    public TrayApp()
    {
        _icon = LoadIcon();
        _iconDimmed = Dim(_icon);

        _sync = new SyncClient(_settings, _ui);
        _sync.StatusChanged += UpdateStatus;
        _sync.TextReceived += OnReceived;

        _tray = new NotifyIcon { Icon = _iconDimmed, Text = "ClipSync", Visible = true, ContextMenuStrip = BuildMenu() };
        _tray.MouseUp += (_, e) =>
        {
            if (e.Button == MouseButtons.Left)
                typeof(NotifyIcon).GetMethod("ShowContextMenu", BindingFlags.Instance | BindingFlags.NonPublic)?.Invoke(_tray, null);
        };
        _tray.BalloonTipClicked += (_, _) =>
        {
            if (_balloonLink != null) OpenLink(_balloonLink);
        };
        _tray.BalloonTipClosed += (_, _) => _balloonLink = null;

        _watcher = new ClipboardWatcher();
        _watcher.Changed += OnClipboardChanged;

        NetworkChange.NetworkAvailabilityChanged += (_, e) =>
        {
            if (e.IsAvailable) _ui.Post(_ => _sync.Start(), null);
        };

        if (string.IsNullOrEmpty(_settings.Code))
        {
            UpdateStatus(SyncStatus.NotSetUp);
            _ui.Post(_ => ShowPairing(firstRun: true), null);
        }
        else
        {
            _sync.Start();
        }
    }

    ContextMenuStrip BuildMenu()
    {
        var menu = new ContextMenuStrip { ShowImageMargin = false };
        _sendItem.Click += (_, _) => SendClipboardNow();
        _autoSendItem.Checked = _settings.AutoSend;
        _autoSendItem.CheckedChanged += (_, _) =>
        {
            _settings.AutoSend = _autoSendItem.Checked;
            _settings.Save();
        };
        _notifyItem.Checked = _settings.ShowNotifications;
        _notifyItem.CheckedChanged += (_, _) =>
        {
            _settings.ShowNotifications = _notifyItem.Checked;
            _settings.Save();
        };
        _startupItem.CheckedChanged += (_, _) => SetStartWithWindows(_startupItem.Checked);
        _pairingItem.Click += (_, _) => ShowPairing(firstRun: false);

        menu.Items.AddRange(new ToolStripItem[]
        {
            _statusItem,
            new ToolStripSeparator(),
            _sendItem,
            _recentMenu,
            new ToolStripSeparator(),
            _autoSendItem,
            _notifyItem,
            _startupItem,
            new ToolStripSeparator(),
            _pairingItem,
            new ToolStripMenuItem("Exit", null, (_, _) => Exit()),
        });
        menu.Opening += (_, _) =>
        {
            _startupItem.Checked = StartsWithWindows();
            RebuildRecent();
        };
        return menu;
    }

    void RebuildRecent()
    {
        _recentMenu.DropDownItems.Clear();
        if (_settings.History.Count == 0)
        {
            _recentMenu.DropDownItems.Add(new ToolStripMenuItem("No items yet") { Enabled = false });
            return;
        }
        foreach (var item in _settings.History.ToList())
        {
            var preview = string.Join(" ", item.Text.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));
            if (preview.Length > 60) preview = preview[..57] + "…";
            var label = (item.Sent ? "↑  You:  " : $"↓  {item.From}:  ") + preview.Replace("&", "&&");
            var entry = new ToolStripMenuItem(label) { ToolTipText = "Click to copy" };
            var text = item.Text;
            entry.Click += (_, _) =>
            {
                _lastSynced = text;
                SetClipboard(text);
            };
            _recentMenu.DropDownItems.Add(entry);
        }
        _recentMenu.DropDownItems.Add(new ToolStripSeparator());
        _recentMenu.DropDownItems.Add(new ToolStripMenuItem("Clear history", null, (_, _) =>
        {
            _settings.History = new List<HistoryItem>();
            _settings.Save();
        }));
    }

    void UpdateStatus(SyncStatus status)
    {
        var (label, dimmed) = status switch
        {
            SyncStatus.Connected => ("Connected", false),
            SyncStatus.Connecting => ("Connecting…", true),
            SyncStatus.Offline => ("No connection — retrying", true),
            SyncStatus.Error => ("Connection problem — retrying", true),
            _ => ("Not set up", true),
        };
        _statusItem.Text = "●  " + label;
        _statusItem.ForeColor = status switch
        {
            SyncStatus.Connected => Color.FromArgb(0x18, 0x86, 0x4B),
            SyncStatus.Offline => Color.FromArgb(0xB4, 0x53, 0x09),
            SyncStatus.Error => Color.FromArgb(0xBA, 0x1A, 0x1A),
            _ => SystemColors.GrayText,
        };
        _tray.Text = "ClipSync — " + (label.Length > 40 ? label[..40] : label);
        _tray.Icon = dimmed ? _iconDimmed : _icon;
        _sendItem.Enabled = status != SyncStatus.NotSetUp;
    }

    void OnClipboardChanged()
    {
        if (!_settings.AutoSend || _sync.Crypto == null) return;
        var text = ReadClipboard(respectPrivacy: true);
        if (string.IsNullOrWhiteSpace(text) || text == _lastSynced || text.Length > 1_000_000) return;
        _lastSynced = text;
        _ = Send(text, quiet: true);
    }

    void SendClipboardNow()
    {
        var text = ReadClipboard(respectPrivacy: false);
        if (string.IsNullOrWhiteSpace(text))
        {
            Balloon("Nothing to send", "Copy some text first.");
            return;
        }
        _lastSynced = text;
        _ = Send(text, quiet: false);
    }

    async Task Send(string text, bool quiet)
    {
        try
        {
            await _sync.SendText(text);
            if (!quiet) Balloon("Sent", "Sent to your devices.");
        }
        catch (Exception e)
        {
            var offline = !NetworkInterface.GetIsNetworkAvailable();
            if (!quiet || !offline) Balloon("Couldn't send", offline ? "You're offline." : e.Message);
        }
    }

    void OnReceived(string text, string from, bool fresh)
    {
        if (!fresh) return; // replayed after being offline: history only
        _lastSynced = text;
        SetClipboard(text);
        if (!_settings.ShowNotifications) return;
        var link = LinkOf(text);
        Balloon(link != null ? $"Link copied from {from}" : $"Copied from {from}",
            link != null ? link + "\nClick to open." : Preview(text));
        _balloonLink = link;
    }

    /// <summary>Reads text from the clipboard, skipping things password managers mark as private.</summary>
    static string? ReadClipboard(bool respectPrivacy)
    {
        for (var attempt = 0; attempt < 5; attempt++)
        {
            try
            {
                var data = Clipboard.GetDataObject();
                if (data == null) return null;
                if (respectPrivacy && IsPrivate(data)) return null;
                return data.GetDataPresent(DataFormats.UnicodeText) ? data.GetData(DataFormats.UnicodeText) as string : null;
            }
            catch (ExternalException)
            {
                Thread.Sleep(50); // another app has the clipboard open
            }
        }
        return null;
    }

    static bool IsPrivate(IDataObject data)
    {
        if (data.GetDataPresent("ExcludeClipboardContentFromMonitorProcessing") || data.GetDataPresent("Clipboard Viewer Ignore"))
            return true;
        if (data.GetDataPresent("CanIncludeInClipboardHistory") &&
            data.GetData("CanIncludeInClipboardHistory") is MemoryStream ms && ms.Length >= 4 &&
            BitConverter.ToInt32(ms.ToArray(), 0) == 0)
            return true;
        return false;
    }

    static void SetClipboard(string text)
    {
        for (var attempt = 0; attempt < 5; attempt++)
        {
            try
            {
                Clipboard.SetText(text);
                return;
            }
            catch (ExternalException)
            {
                Thread.Sleep(50);
            }
        }
    }

    void Balloon(string title, string text)
    {
        _balloonLink = null;
        _tray.ShowBalloonTip(3000, title, string.IsNullOrWhiteSpace(text) ? " " : text, ToolTipIcon.None);
    }

    void ShowPairing(bool firstRun)
    {
        using var form = new SetupForm(_settings, _icon, firstRun);
        var result = form.ShowDialog();
        if (form.Disconnected)
        {
            _sync.Stop();
            _settings.Code = "";
            _settings.Devices.Clear();
            _settings.Save();
            UpdateStatus(SyncStatus.NotSetUp);
            return;
        }
        if (result != DialogResult.OK) return;

        var codeChanged = form.Code != _settings.Code;
        var nameChanged = form.DeviceName != _settings.DeviceName;
        _settings.DeviceName = form.DeviceName;
        if (codeChanged)
        {
            _settings.Code = form.Code;
            _settings.Devices.Clear();
        }
        _settings.Save();
        if (codeChanged)
        {
            _sync.Start();
            if (firstRun) SetStartWithWindows(true);
            Balloon("ClipSync is on", "Copy something here and it appears on your phones. Click the tray icon for options.");
        }
        else if (nameChanged)
        {
            _ = _sync.SendHello(false).ContinueWith(_ => { });
        }
    }

    static bool StartsWithWindows()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKey);
        return key?.GetValue("ClipSync") != null;
    }

    static void SetStartWithWindows(bool enabled)
    {
        using var key = Registry.CurrentUser.CreateSubKey(RunKey);
        if (enabled) key.SetValue("ClipSync", $"\"{Environment.ProcessPath}\"");
        else key.DeleteValue("ClipSync", throwOnMissingValue: false);
    }

    static string? LinkOf(string text)
    {
        var t = text.Trim();
        if (t.Length == 0 || t.Any(char.IsWhiteSpace)) return null;
        var candidate = t.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || t.StartsWith("https://", StringComparison.OrdinalIgnoreCase)
            ? t : "https://" + t;
        return Uri.TryCreate(candidate, UriKind.Absolute, out var uri) && uri.Host.Contains('.') ? uri.ToString() : null;
    }

    static void OpenLink(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch
        {
            // no browser registered
        }
    }

    static string Preview(string text)
    {
        var t = text.Trim();
        return t.Length > 200 ? t[..200] + "…" : t;
    }

    static Icon LoadIcon()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("clipsync.ico")!;
        return new Icon(stream, SystemInformation.SmallIconSize);
    }

    /// <summary>Faded copy of the icon, shown while not connected.</summary>
    static Icon Dim(Icon icon)
    {
        using var source = icon.ToBitmap();
        using var faded = new Bitmap(source.Width, source.Height);
        using (var g = Graphics.FromImage(faded))
        {
            var matrix = new ColorMatrix
            {
                Matrix00 = 0.33f, Matrix01 = 0.33f, Matrix02 = 0.33f,
                Matrix10 = 0.33f, Matrix11 = 0.33f, Matrix12 = 0.33f,
                Matrix20 = 0.33f, Matrix21 = 0.33f, Matrix22 = 0.33f,
                Matrix33 = 0.6f,
            };
            using var attributes = new ImageAttributes();
            attributes.SetColorMatrix(matrix);
            g.DrawImage(source, new Rectangle(0, 0, source.Width, source.Height), 0, 0, source.Width, source.Height, GraphicsUnit.Pixel, attributes);
        }
        return Icon.FromHandle(faded.GetHicon());
    }

    void Exit()
    {
        _sync.Stop();
        _watcher.Dispose();
        _tray.Visible = false;
        _tray.Dispose();
        ExitThread();
    }
}
