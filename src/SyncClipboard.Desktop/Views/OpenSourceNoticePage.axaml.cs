using Avalonia.Controls;
using Avalonia.Interactivity;
using FluentAvalonia.UI.Controls;
using FluentAvalonia.UI.Navigation;
using Microsoft.Extensions.DependencyInjection;
using SyncClipboard.Core.I18n;
using SyncClipboard.Core.Models;
using SyncClipboard.Core.Utilities;
using SyncClipboard.Core.ViewModels;

namespace SyncClipboard.Desktop.Views;

public partial class OpenSourceNoticePage : UserControl
{
    private readonly OpenSourceNoticeViewModel _viewModel;

    public OpenSourceNoticePage()
    {
        AddHandler(Frame.NavigatedToEvent, OnNavigatedTo, RoutingStrategies.Direct);
        _viewModel = App.Current.Services.GetRequiredService<OpenSourceNoticeViewModel>();
        DataContext = _viewModel;
        InitializeComponent();
        UpdateActionUi();
    }

    private void OnNavigatedTo(object? sender, NavigationEventArgs e)
    {
        _viewModel.IsFirstRun = e.Parameter is not false;
        UpdateActionUi();
    }

    private void UpdateActionUi()
    {
        _PrimaryAction.Content = _viewModel.IsFirstRun ? Strings.OssIUnderstandContinue : Strings.OssClose;
    }

    private void PrimaryAction_Click(object? sender, RoutedEventArgs e)
    {
        if (_viewModel.IsFirstRun)
        {
            _viewModel.Acknowledged += OnAcknowledgedOnce;
            _viewModel.AcknowledgeCommand.Execute(null);
        }
        else
        {
            App.Current.MainWindow.NavigateToLastLevel();
        }
    }

    private void OnAcknowledgedOnce()
    {
        _viewModel.Acknowledged -= OnAcknowledgedOnce;
        App.Current.MainWindow.OpenPage(PageDefinition.SyncSetting);
    }

    private void ForkButton_Click(object? sender, RoutedEventArgs e)
    {
        _viewModel.OpenForkRepositoryCommand.Execute(null);
    }

    private void UpstreamButton_Click(object? sender, RoutedEventArgs e)
    {
        _viewModel.OpenUpstreamRepositoryCommand.Execute(null);
    }

    private async void ViewLicense_Click(object? sender, RoutedEventArgs e)
    {
        var dialog = new ContentDialog
        {
            Title = Strings.License,
            CloseButtonText = Strings.OssClose,
            Content = new ScrollViewer
            {
                MaxHeight = 420,
                Content = new TextBlock
                {
                    Text = _viewModel.ProjectLicenseText,
                    TextWrapping = Avalonia.Media.TextWrapping.Wrap
                }
            }
        };
        await dialog.ShowAsync();
    }

    private void DependencyHome_Click(object? sender, RoutedEventArgs e)
    {
        var url = ((HyperlinkButton?)sender)?.Content as string;
        Sys.OpenWithDefaultApp(url);
    }

    private void DependencyLicense_Click(object? sender, RoutedEventArgs e)
    {
        if ((sender as SettingsExpanderItem)?.Content is not OpenSourceSoftware software)
        {
            return;
        }
        if (!software.IsValidLicensePath)
        {
            return;
        }
        App.Current.MainWindow.NavigateToNextLevel(PageDefinition.License, software.LicensePath);
    }
}
