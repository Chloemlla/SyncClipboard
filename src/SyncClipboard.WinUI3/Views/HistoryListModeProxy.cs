using System.ComponentModel;

namespace SyncClipboard.WinUI3.Views;

public class HistoryListModeProxy : INotifyPropertyChanged
{
    public static HistoryListModeProxy Current { get; } = new();

    public event PropertyChangedEventHandler? PropertyChanged;

    private bool _isCompactListMode;

    public bool IsCompactListMode
    {
        get => _isCompactListMode;
        set
        {
            if (_isCompactListMode == value)
            {
                return;
            }

            _isCompactListMode = value;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsCompactListMode)));
        }
    }
}
