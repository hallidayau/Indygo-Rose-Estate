using System.Runtime.InteropServices;

namespace ClipSync;

/// <summary>Hidden message-only window that Windows notifies whenever the clipboard changes.</summary>
sealed class ClipboardWatcher : NativeWindow, IDisposable
{
    const int WM_CLIPBOARDUPDATE = 0x031D;
    static readonly IntPtr HWND_MESSAGE = new(-3);

    [DllImport("user32.dll", SetLastError = true)]
    static extern bool AddClipboardFormatListener(IntPtr hwnd);

    [DllImport("user32.dll", SetLastError = true)]
    static extern bool RemoveClipboardFormatListener(IntPtr hwnd);

    public event Action? Changed;

    public ClipboardWatcher()
    {
        CreateHandle(new CreateParams { Parent = HWND_MESSAGE });
        AddClipboardFormatListener(Handle);
    }

    protected override void WndProc(ref Message m)
    {
        if (m.Msg == WM_CLIPBOARDUPDATE) Changed?.Invoke();
        base.WndProc(ref m);
    }

    public void Dispose()
    {
        RemoveClipboardFormatListener(Handle);
        DestroyHandle();
    }
}
