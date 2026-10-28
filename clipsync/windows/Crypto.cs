using System.Security.Cryptography;
using System.Text;

namespace ClipSync;

/// <summary>
/// Same scheme as the Android app (Crypto.kt): the relay topic and the AES-256-GCM key
/// both come from the pairing code. Encrypted data is IV (12 bytes) + ciphertext + tag (16 bytes).
/// </summary>
public sealed class Crypto
{
    public string Topic { get; }
    readonly byte[] _key;

    public Crypto(string code)
    {
        var digest = SHA256.HashData(Encoding.UTF8.GetBytes("clipsync-topic-v1:" + code));
        Topic = "clipsync_" + Convert.ToHexString(digest).ToLowerInvariant()[..40];
        _key = Rfc2898DeriveBytes.Pbkdf2(
            Encoding.UTF8.GetBytes(code), Encoding.UTF8.GetBytes("clipsync-key-v1"),
            100_000, HashAlgorithmName.SHA256, 32);
    }

    public byte[] Encrypt(byte[] plain)
    {
        var iv = RandomNumberGenerator.GetBytes(12);
        var cipher = new byte[plain.Length];
        var tag = new byte[16];
        using var aes = new AesGcm(_key, 16);
        aes.Encrypt(iv, plain, cipher, tag);
        return [.. iv, .. cipher, .. tag];
    }

    public byte[] Decrypt(byte[] data)
    {
        var iv = data.AsSpan(0, 12);
        var cipher = data.AsSpan(12, data.Length - 28);
        var tag = data.AsSpan(data.Length - 16);
        var plain = new byte[cipher.Length];
        using var aes = new AesGcm(_key, 16);
        aes.Decrypt(iv, cipher, tag, plain);
        return plain;
    }

    public string EncryptString(string text) => Convert.ToBase64String(Encrypt(Encoding.UTF8.GetBytes(text)));

    public string DecryptString(string b64) => Encoding.UTF8.GetString(Decrypt(Convert.FromBase64String(b64)));

    // No 0/O/1/I/L so the code can't be mistyped.
    const string Alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /// <summary>Accepts any spacing, dashes or case; null unless it's a full 16-character code.</summary>
    public static string? NormalizeCode(string input)
    {
        var raw = new string(input.ToUpperInvariant().Where(c => c is >= 'A' and <= 'Z' or >= '0' and <= '9').ToArray());
        return raw.Length == 16 ? FormatCode(raw) : null;
    }

    public static string FormatCode(string raw) => string.Join("-", raw.Chunk(4).Select(c => new string(c)));

    public static string GenerateCode() =>
        FormatCode(new string(Enumerable.Range(0, 16).Select(_ => Alphabet[RandomNumberGenerator.GetInt32(Alphabet.Length)]).ToArray()));
}
