namespace ClipSync;

static class Program
{
    [STAThread]
    static void Main()
    {
        // Only one copy runs at a time.
        using var mutex = new Mutex(true, "ClipSync-tray-single-instance", out bool first);
        if (!first) return;

        ApplicationConfiguration.Initialize();
        // A tray app should never die from a stray error.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => System.Diagnostics.Debug.WriteLine(e.Exception);
        SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
        Application.Run(new TrayApp());
    }
}
