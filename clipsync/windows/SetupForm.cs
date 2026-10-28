namespace ClipSync;

/// <summary>Enter the pairing code from the phone, name this PC, or disconnect.</summary>
sealed class SetupForm : Form
{
    static readonly Color Brand = Color.FromArgb(0x25, 0x63, 0xEB);
    static readonly Color Muted = Color.FromArgb(0x64, 0x74, 0x8B);
    static readonly Color ErrorRed = Color.FromArgb(0xBA, 0x1A, 0x1A);

    readonly TextBox _code;
    readonly TextBox _name;
    readonly Label _error;
    bool _formatting;

    public string Code { get; private set; } = "";
    public string DeviceName { get; private set; } = "";
    public bool Disconnected { get; private set; }

    public SetupForm(Settings settings, Icon icon, bool firstRun)
    {
        Text = "ClipSync";
        Icon = icon;
        Font = new Font("Segoe UI", 10f);
        BackColor = Color.White;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        TopMost = true;
        AutoScaleMode = AutoScaleMode.Dpi;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        Padding = new Padding(28, 24, 28, 20);

        var layout = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
        };
        Controls.Add(layout);
        const int width = 380;

        layout.Controls.Add(new Label
        {
            Text = firstRun || settings.Code == "" ? "Connect this PC" : "Pairing & devices",
            Font = new Font("Segoe UI Semibold", 16f),
            AutoSize = true,
            Margin = new Padding(0, 0, 0, 4),
        });
        layout.Controls.Add(new Label
        {
            Text = "Enter the pairing code from ClipSync on your phone (Settings → Paired device). Every device uses the same code.",
            ForeColor = Muted,
            MaximumSize = new Size(width, 0),
            AutoSize = true,
            Margin = new Padding(0, 0, 0, 16),
        });

        layout.Controls.Add(FieldLabel("Pairing code"));
        _code = new TextBox
        {
            Width = width,
            Font = new Font("Consolas", 15f),
            CharacterCasing = CharacterCasing.Upper,
            MaxLength = 19,
            PlaceholderText = "XXXX-XXXX-XXXX-XXXX",
            Text = settings.Code,
        };
        _code.TextChanged += (_, _) => FormatCode();
        layout.Controls.Add(_code);
        _error = new Label { ForeColor = ErrorRed, AutoSize = true, Margin = new Padding(0, 2, 0, 8) };
        layout.Controls.Add(_error);

        layout.Controls.Add(FieldLabel("Name for this PC"));
        _name = new TextBox { Width = width, MaxLength = 30, Text = settings.DeviceName };
        layout.Controls.Add(_name);

        if (settings.Devices.Count > 0)
        {
            layout.Controls.Add(new Label
            {
                Text = "Connected devices: " + string.Join(", ", settings.Devices.Values.Distinct()),
                ForeColor = Muted,
                MaximumSize = new Size(width, 0),
                AutoSize = true,
                Margin = new Padding(0, 12, 0, 0),
            });
        }

        var buttons = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.RightToLeft,
            Width = width,
            AutoSize = true,
            Margin = new Padding(0, 20, 0, 0),
        };
        var save = new Button
        {
            Text = "Save && connect",
            BackColor = Brand,
            ForeColor = Color.White,
            FlatStyle = FlatStyle.Flat,
            AutoSize = true,
            Padding = new Padding(14, 6, 14, 6),
            Margin = new Padding(8, 0, 0, 0),
        };
        save.FlatAppearance.BorderSize = 0;
        save.Click += (_, _) => Save();
        var cancel = new Button
        {
            Text = "Cancel",
            FlatStyle = FlatStyle.Flat,
            AutoSize = true,
            Padding = new Padding(10, 6, 10, 6),
            DialogResult = DialogResult.Cancel,
        };
        cancel.FlatAppearance.BorderColor = Color.FromArgb(0xCB, 0xD5, 0xE1);
        buttons.Controls.Add(save);
        buttons.Controls.Add(cancel);
        if (settings.Code != "")
        {
            var disconnect = new LinkLabel
            {
                Text = "Disconnect this PC",
                LinkColor = ErrorRed,
                AutoSize = true,
                Margin = new Padding(0, 10, 40, 0),
            };
            disconnect.LinkClicked += (_, _) =>
            {
                if (MessageBox.Show(this, "Stop syncing on this PC until you enter the code again?", "ClipSync",
                        MessageBoxButtons.OKCancel, MessageBoxIcon.Question) != DialogResult.OK) return;
                Disconnected = true;
                Close();
            };
            buttons.Controls.Add(disconnect);
        }
        layout.Controls.Add(buttons);

        AcceptButton = save;
        CancelButton = cancel;
    }

    static Label FieldLabel(string text) => new()
    {
        Text = text,
        ForeColor = Muted,
        Font = new Font("Segoe UI", 9f),
        AutoSize = true,
        Margin = new Padding(0, 4, 0, 2),
    };

    /// <summary>Adds the dashes as the code is typed or pasted.</summary>
    void FormatCode()
    {
        if (_formatting) return;
        _error.Text = "";
        var raw = new string(_code.Text.ToUpperInvariant().Where(c => c is >= 'A' and <= 'Z' or >= '0' and <= '9').Take(16).ToArray());
        var formatted = Crypto.FormatCode(raw);
        if (formatted == _code.Text) return;
        _formatting = true;
        _code.Text = formatted;
        _code.SelectionStart = formatted.Length;
        _formatting = false;
    }

    void Save()
    {
        var code = Crypto.NormalizeCode(_code.Text);
        if (code == null)
        {
            _error.Text = "Check the code and try again.";
            _code.Focus();
            return;
        }
        Code = code;
        DeviceName = string.IsNullOrWhiteSpace(_name.Text) ? Environment.MachineName : _name.Text.Trim();
        DialogResult = DialogResult.OK;
        Close();
    }
}
