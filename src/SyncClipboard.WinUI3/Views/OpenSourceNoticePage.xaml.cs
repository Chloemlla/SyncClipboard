using CommunityToolkit.WinUI.Controls;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using SyncClipboard.Core.I18n;
using SyncClipboard.Core.Interfaces;
using SyncClipboard.Core.Models;
using SyncClipboard.Core.Utilities;
using SyncClipboard.Core.ViewModels;
using System;

namespace SyncClipboard.WinUI3.Views
{
    public sealed partial class OpenSourceNoticePage : Page
    {
        private readonly OpenSourceNoticeViewModel _viewModel;
        private readonly IMainWindow _mainWindow;
        private ContentDialog? _licenseDialog;

        public OpenSourceNoticePage()
        {
            this.InitializeComponent();
            _viewModel = App.Current.Services.GetRequiredService<OpenSourceNoticeViewModel>();
            _mainWindow = App.Current.Services.GetRequiredService<IMainWindow>();
            DataContext = _viewModel;
            _viewModel.Acknowledged += OnAcknowledged;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            // Default is first-run; About can pass false for browse mode.
            _viewModel.IsFirstRun = e.Parameter is not false;
            UpdateActionUi();
        }

        private void UpdateActionUi()
        {
            _PrimaryAction.Content = _viewModel.IsFirstRun ? Strings.OssIUnderstandContinue : Strings.OssClose;
        }

        private void PrimaryAction_Click(object _, RoutedEventArgs _1)
        {
            if (_viewModel.IsFirstRun)
            {
                _viewModel.AcknowledgeCommand.Execute(null);
            }
            else
            {
                _mainWindow.NavigateToLastLevel();
            }
        }

        private void OnAcknowledged()
        {
            _mainWindow.OpenPage(PageDefinition.SyncSetting, null);
        }

        private void ForkButton_Click(object _, RoutedEventArgs _1)
        {
            _viewModel.OpenForkRepositoryCommand.Execute(null);
        }

        private void UpstreamButton_Click(object _, RoutedEventArgs _1)
        {
            _viewModel.OpenUpstreamRepositoryCommand.Execute(null);
        }

        private async void ViewLicense_Click(object _, RoutedEventArgs _1)
        {
            _licenseDialog ??= new ContentDialog
            {
                Title = Strings.License,
                CloseButtonText = Strings.OssClose,
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot,
                Content = new ScrollViewer
                {
                    MaxHeight = 420,
                    Content = new TextBlock
                    {
                        Text = _viewModel.ProjectLicenseText,
                        TextWrapping = TextWrapping.WrapWholeWords,
                        IsTextSelectionEnabled = true
                    }
                }
            };
            _licenseDialog.XamlRoot = this.XamlRoot;
            await _licenseDialog.ShowAsync();
        }

        private void DependencyHome_Click(object sender, RoutedEventArgs _)
        {
            if (sender is HyperlinkButton button && button.Tag is string url)
            {
                Sys.OpenWithDefaultApp(url);
            }
        }

        private void DependencyLicense_Click(object sender, RoutedEventArgs _)
        {
            if (sender is FrameworkElement element && element.Tag is OpenSourceSoftware software && software.IsValidLicensePath)
            {
                _mainWindow.NavigateToNextLevel(PageDefinition.License, software.LicensePath);
            }
        }
    }
}
