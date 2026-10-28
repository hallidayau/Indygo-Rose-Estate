using System.Text.Json;

namespace ClipSync;

public class HistoryItem
{
    public bool Sent { get; set; }
    public string Text { get; set; } = "";
    public string From { get; set; } = "";
    public DateTimeOffset At { get; set; }
}

/// <summary>Stored as JSON in %APPDATA%\ClipSync\settings.json.</summary>
public class Settings
{
    public string Code { get; set; } = "";
    public string DeviceId { get; set; } = Guid.NewGuid().ToString();
    public string DeviceName { get; set; } = Environment.MachineName;
    public bool AutoSend { get; set; } = true;
    public bool ShowNotifications { get; set; } = true;
    public Dictionary<string, string> LastMessageIds { get; set; } = new();
    public Dictionary<string, string> Devices { get; set; } = new();
    public List<HistoryItem> History { get; set; } = new();

    public const int HistoryLimit = 10;

    static readonly string Folder =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ClipSync");
    static readonly string FilePath = Path.Combine(Folder, "settings.json");
    static readonly JsonSerializerOptions Json = new() { WriteIndented = true };
    readonly object _lock = new();

    public static Settings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) ?? new Settings();
        }
        catch
        {
            // Corrupt file: start fresh.
        }
        return new Settings();
    }

    public void Save()
    {
        lock (_lock)
        {
            try
            {
                Directory.CreateDirectory(Folder);
                var tmp = FilePath + ".tmp";
                File.WriteAllText(tmp, JsonSerializer.Serialize(this, Json));
                File.Move(tmp, FilePath, overwrite: true);
            }
            catch (Exception e)
            {
                System.Diagnostics.Debug.WriteLine("Couldn't save settings: " + e.Message);
            }
        }
    }

    public void AddHistory(bool sent, string text, string from, DateTimeOffset at)
    {
        lock (_lock)
        {
            // Replace rather than mutate, so the tray menu can read the old list safely.
            var item = new HistoryItem { Sent = sent, Text = text, From = from, At = at };
            History = History.Prepend(item).OrderByDescending(h => h.At).Take(HistoryLimit).ToList();
        }
        Save();
    }
}
