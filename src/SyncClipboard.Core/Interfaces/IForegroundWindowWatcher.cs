namespace SyncClipboard.Core.Interfaces;

public interface IForegroundWindowWatcher : IDisposable
{
    event Action? ForegroundWindowChanged;

    void Start();
    void Stop();
}
