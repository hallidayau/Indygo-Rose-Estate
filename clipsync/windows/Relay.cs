using System.Net.Http.Headers;

namespace ClipSync;

/// <summary>Minimal client for the free ntfy.sh relay. Everything sent is already encrypted.</summary>
public static class Relay
{
    public const string BaseUrl = "https://ntfy.sh";
    static readonly HttpClient Http = new() { Timeout = Timeout.InfiniteTimeSpan };

    public static async Task PublishMessage(string topic, string message)
    {
        using var content = new StringContent(message);
        using var res = await Http.PostAsync($"{BaseUrl}/{topic}", content, Timeout30());
        Ensure(res);
    }

    public static async Task PublishAttachment(string topic, string message, byte[] data)
    {
        using var req = new HttpRequestMessage(HttpMethod.Put, $"{BaseUrl}/{topic}") { Content = new ByteArrayContent(data) };
        req.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        req.Headers.Add("X-Message", message);
        req.Headers.Add("X-Filename", "clip.bin");
        using var res = await Http.SendAsync(req, Timeout30());
        Ensure(res);
    }

    public static async Task<byte[]> Download(string url)
    {
        using var res = await Http.GetAsync(url, Timeout30());
        Ensure(res);
        return await res.Content.ReadAsByteArrayAsync();
    }

    /// <summary>Opens the long-lived stream of new messages (one JSON object per line).</summary>
    public static async Task<HttpResponseMessage> Subscribe(string topic, string? since, CancellationToken ct)
    {
        var url = $"{BaseUrl}/{topic}/json" + (since != null ? "?since=" + since : "");
        var res = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct);
        Ensure(res);
        return res;
    }

    static CancellationToken Timeout30() => new CancellationTokenSource(TimeSpan.FromSeconds(30)).Token;

    static void Ensure(HttpResponseMessage res)
    {
        if (!res.IsSuccessStatusCode) throw new HttpRequestException($"Relay error {(int)res.StatusCode}");
    }
}
