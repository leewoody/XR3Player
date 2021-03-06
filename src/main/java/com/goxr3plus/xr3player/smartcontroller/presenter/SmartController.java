/*
 * 
 */
package main.java.com.goxr3plus.xr3player.smartcontroller.presenter;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.fxmisc.richtext.InlineCssTextArea;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTabPane;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import main.java.com.goxr3plus.xr3player.application.Main;
import main.java.com.goxr3plus.xr3player.application.settings.ApplicationSettingsController.SettingsTab;
import main.java.com.goxr3plus.xr3player.application.tools.ActionTool;
import main.java.com.goxr3plus.xr3player.application.tools.InfoTool;
import main.java.com.goxr3plus.xr3player.application.tools.NotificationType;
import main.java.com.goxr3plus.xr3player.smartcontroller.enums.Genre;
import main.java.com.goxr3plus.xr3player.smartcontroller.media.Media;
import main.java.com.goxr3plus.xr3player.smartcontroller.modes.SmartControllerFoldersMode;
import main.java.com.goxr3plus.xr3player.smartcontroller.services.FilesExportService;
import main.java.com.goxr3plus.xr3player.smartcontroller.services.InputService;
import main.java.com.goxr3plus.xr3player.smartcontroller.services.LoadService;
import main.java.com.goxr3plus.xr3player.smartcontroller.tags.TagTabCategory;

/**
 * Used to control big amounts of Media using a TableViewer mechanism
 *
 * @author GOXR3PLUS
 */
public class SmartController extends StackPane {
	
	//----------------------------------------------------------------
	
	@FXML
	private JFXTabPane modesTabPane;
	
	@FXML
	private Tab normalModeTab;
	
	@FXML
	private BorderPane mainBorder;
	
	@FXML
	private StackPane centerStackPane;
	
	@FXML
	private InlineCssTextArea detailCssTextArea;
	
	@FXML
	private HBox searchBarHBox;
	
	@FXML
	private JFXCheckBox instantSearch;
	
	@FXML
	private HBox navigationHBox;
	
	@FXML
	private Button previous;
	
	@FXML
	private TextField pageField;
	
	@FXML
	private Button goToPage;
	
	@FXML
	private Button next;
	
	@FXML
	private MenuButton toolsMenuButton;
	
	@FXML
	private ContextMenu toolsContextMenu;
	
	@FXML
	private MenuItem importFolder;
	
	@FXML
	private MenuItem importFiles;
	
	@FXML
	private MenuItem exportFiles;
	
	@FXML
	private MenuItem showSettings;
	
	@FXML
	private MenuItem clearAll;
	
	@FXML
	private Tab foldersModeTab;
	
	@FXML
	private VBox indicatorVBox;
	
	@FXML
	private ProgressIndicator indicator;
	
	@FXML
	private Button cancelButton;
	
	@FXML
	private TextArea informationTextArea;
	
	@FXML
	private VBox reloadVBox;
	
	@FXML
	private Button reloadPlayListButton;
	
	// ----------------------------------------------------------
	
	private final Genre genre;
	
	/**
	 * The name of the database table (eg. @see
	 * ActionTool.returnRandomTableName())
	 */
	private final String dataBaseTableName;
	
	/** The name of the SmartController . */
	private String controllerName;
	
	/** Total items in database table . */
	private IntegerProperty totalInDataBase = new SimpleIntegerProperty(0);
	
	/**
	 * The last focus owner of the Scene , it is used by the pageField TextField
	 */
	private Node focusOwner;
	
	/** The table viewer. */
	private final MediaTableViewer tableViewer;
	
	/** This list keeps all the Media Items from the TableViewer */
	private final ObservableList<Media> itemsObservableList = FXCollections.observableArrayList();
	
	/** The current page inside the TableViewer */
	private IntegerProperty currentPage = new SimpleIntegerProperty(0);
	
	/** Maximum items allowed per page. */
	private int maximumPerPage = 50;
	
	// ---------Services--------------------------
	
	/** The search service. */
	private final SmartControllerSearcher searchService;
	
	/** The load service. */
	private final LoadService loadService;
	
	/** The input service. */
	private final InputService inputService;
	
	/** CopyOrMoveService */
	private final FilesExportService copyOrMoveService;
	
	// ---------Security---------------------------
	
	/** The deposit working. */
	public volatile boolean depositWorking;
	
	/** The un deposit working. */
	public volatile boolean undepositWorking;
	
	/** The rename working. */
	public volatile boolean renameWorking;
	
	/** The update working. */
	public volatile boolean updateWorking;
	
	// --------------------------------------------------
	
	public final SmartControllerFoldersMode foldersMode = new SmartControllerFoldersMode(this);
	
	// --------------------------------------------------
	
	/**
	 * The Vertical ScrollBar position of SmartController TableViewer without
	 * the search activated
	 */
	private double verticalScrollValueWithoutSearch = -1;
	
	/**
	 * The Vertical ScrollBar position of SmartController TableViewer when the
	 * the search activated
	 */
	private double verticalScrollValueWithSearch = -1;
	
	//--------------------------------------------------
	
	private String previousCancelText = "";
	
	//------------------------------------------------------------------------------------------------------------------------
	
	/**
	 * Instantiates a new smart controller.
	 *
	 * @param genre
	 *            .. @see Genre
	 * @param controllerName
	 *            The name of the SmartController
	 * @param dataBaseTableName
	 *            The name of the database table <br>
	 *            ..@see ActionTool.returnRandomTableName()
	 */
	public SmartController(Genre genre, String controllerName, String dataBaseTableName) {
		this.genre = genre;
		this.controllerName = controllerName;
		this.dataBaseTableName = dataBaseTableName;
		
		// Initialise
		tableViewer = new MediaTableViewer(this);
		searchService = new SmartControllerSearcher(this);
		loadService = new LoadService(this);
		inputService = new InputService(this);
		copyOrMoveService = new FilesExportService(this);
		
		// --------------------------------FXMLLoader---------------------------------------------
		FXMLLoader loader = new FXMLLoader(getClass().getResource(InfoTool.FXMLS + "SmartController.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		
		try {
			loader.load();
		} catch (IOException ex) {
			Main.logger.log(Level.WARNING, "", ex);
		}
		
	}
	
	//---------------------------------------------------------------------------------------------------------------------
	
	/**
	 * Called as soon as FXML file has been loaded
	 */
	@FXML
	private void initialize() {
		
		// ------ centerStackPane
		centerStackPane.setOnKeyReleased(key -> {
			KeyCode code = key.getCode();
			
			if (key.isControlDown() && code == KeyCode.LEFT)
				goPrevious();
			else if (key.isControlDown() && code == KeyCode.RIGHT)
				goNext();
			else if (tableViewer.getSelectedCount() > 0) { // TableViewer
				
				if (code == KeyCode.DELETE && SmartController.this.genre != Genre.SEARCHWINDOW)
					prepareDelete(key.isShiftDown());
				else if (key.isControlDown()) { //Short Cuts
					if (code == KeyCode.F)
						ActionTool.openFileLocation(tableViewer.getSelectionModel().getSelectedItem().getFilePath());
					else if (code == KeyCode.Q)
						tableViewer.getSelectionModel().getSelectedItem().updateStars(tableViewer);
					else if (code == KeyCode.R)
						tableViewer.getSelectionModel().getSelectedItem().rename(tableViewer);
					else if (code == KeyCode.U) {
						Media media = tableViewer.getSelectionModel().getSelectedItem();
						if (!Main.playedSongs.containsFile(media.getFilePath()))
							Main.playedSongs.add(media.getFilePath(), true);
						else
							Main.playedSongs.remove(media.getFilePath(), true);
					} else if (code == KeyCode.ENTER)
						Main.xPlayersList.getXPlayerController(0).playSong(tableViewer.getSelectionModel().getSelectedItem().getFilePath());
					else if (key.isControlDown() && code == KeyCode.I)
						//More than 1 selected?
						if (tableViewer.getSelectedCount() > 1)
							Main.tagWindow.openMultipleAudioFiles(tableViewer.getSelectionModel().getSelectedItems().stream().map(Media::getFilePath)
									.collect(Collectors.toCollection(FXCollections::observableArrayList)), tableViewer.getSelectionModel().getSelectedItem().getFilePath());
						//Only one file selected
						else
							Main.tagWindow.openAudio(tableViewer.getSelectionModel().getSelectedItem().getFilePath(), TagTabCategory.BASICINFO, true);
				}
				
			}
			
		});
		
		// ------ tableViewer	
		centerStackPane.getChildren().add(tableViewer);
		tableViewer.toBack();
		
		// ------ region
		//indicatorVBox.setVisible(false);
		
		// FunIndicator
		//	FunIndicator funIndicator = new FunIndicator();
		//	//super.getChildren().add(super.getChildren().size() - 1, funIndicator);
		//	funIndicator.setPrefSize(50, 50);
		//	indicatorVBox.getChildren().add(0, funIndicator);
		//	region.visibleProperty().addListener((observable, oldValue, newValue) -> {
		//	    if (!region.isVisible())
		//		funIndicator.pause();
		//	    else {
		//		funIndicator.setFromColor(Color.WHITE);
		//		funIndicator.start();
		//	    }
		//	});
		
		// ------ progress indicator
		//funIndicator.visibleProperty().bind(region.visibleProperty());
		indicator.setVisible(true);
		//indicator.visibleProperty().bind(region.visibleProperty())
		indicatorVBox.setVisible(false);
		
		// ------ cancel
		cancelButton.hoverProperty().addListener((observable , oldValue , newValue) -> cancelButton.setText(cancelButton.isHover() ? "cancel" : previousCancelText));
		cancelButton.textProperty().addListener((observable , oldValue , newValue) -> {
			if (!"cancel".equals(cancelButton.getText())) {
				previousCancelText = cancelButton.getText();
				
				//Change it if it is hovered
				if (cancelButton.isHover())
					cancelButton.setText("cancel");
			}
		});
		//cancel.visibleProperty().bind(region.visibleProperty())
		cancelButton.setVisible(true);
		cancelButton.setDisable(true);
		
		// ------ searchBarHBox
		searchBarHBox.getChildren().add(1, searchService);
		
		//------navigationHBox
		//navigationHBox.disableProperty().bind(this.totalInDataBase.isEqualTo(0))
		
		// ------ previous
		//previous.opacityProperty()
		//	.bind(Bindings.when(previous.hoverProperty().or(next.hoverProperty())).then(1.0).otherwise(0.5))
		
		//previous.disableProperty().bind(next.disabledProperty())
		previous.disableProperty().bind(currentPage.isEqualTo(0));
		previous.setOnAction(a -> goPrevious());
		
		// ------- next
		//next.opacityProperty()
		//	.bind(Bindings.when(next.hoverProperty().or(previous.hoverProperty())).then(1.0).otherwise(0.5))
		next.setDisable(true);
		next.setOnAction(a -> goNext());
		
		//Handler
		EventHandler<ActionEvent> handler = ac -> {
			if (!pageField.getText().isEmpty() && !loadService.isRunning() && !searchService.getService().isRunning() && totalInDataBase.get() != 0) {
				int listNumber = Integer.parseInt(pageField.getText());
				if (listNumber <= getMaximumList()) {
					currentPage.set(listNumber);
					loadService.startService(false, true, false);
				} else {
					pageField.setText(Integer.toString(listNumber));
					pageField.selectEnd();
				}
			}
		};
		
		//-----goToPage
		goToPage.setOnAction(handler);
		//goToPage.disableProperty().bind(currentPage.isEqualTo(Integer.valueOf(pageField.getText())))
		
		// -------- pageField
		//pageField.opacityProperty()
		//	.bind(Bindings.when(pageField.hoverProperty().or(next.hoverProperty()).or(previous.hoverProperty()))
		//		.then(1.0).otherwise(0.03))
		
		//pageField.disableProperty().bind(next.disabledProperty())
		pageField.textProperty().addListener((observable , oldValue , newValue) -> {
			
			if (!newValue.matches("\\d"))
				pageField.setText(newValue.replaceAll("\\D", ""));
			
			if (!pageField.getText().isEmpty()) {
				// System.out.println("Setting")
				int maximumPage = getMaximumList();
				// System.out.println("maximum Page:"+maximumPage)
				if (Integer.parseInt(pageField.getText()) > maximumPage)
					Platform.runLater(() -> {
						pageField.setText(Integer.toString(maximumPage));
						pageField.selectEnd();
					});
				
			}
			//	    System.out.println("CurrentPape:" + currentPage.get() + " , PageField:" + Integer.valueOf(pageField.getText()) + " ->"
			//		    + currentPage.isEqualTo(Integer.valueOf(pageField.getText())).get() + " , Property:"
			//		    + currentPage.isEqualTo(Integer.valueOf(pageField.getText())));
			
		});
		
		pageField.setOnAction(handler);
		pageField.setOnScroll(scroll -> { // SCROLL
			
			//Calculate the Value
			int current = Integer.parseInt(pageField.getText());
			if (scroll.getDeltaY() > 0 && current < getMaximumList())
				++current;
			else if (scroll.getDeltaY() < 0 && current >= 1)
				--current;
			
			// Update pageField
			pageField.setText(String.valueOf(current));
			pageField.selectEnd();
			pageField.deselect();
			
		});
		
		//When the PageField is being hovered
		pageField.hoverProperty().addListener(l -> {
			if (!pageField.isHover())
				focusOwner.requestFocus();
			else {
				focusOwner = Main.window.getScene().getFocusOwner();
				pageField.requestFocus();
				pageField.selectEnd();
			}
		});
		
		//showSettings
		showSettings.setOnAction(a -> Main.settingsWindow.showWindow(SettingsTab.PLAYLISTS));
		
		//importFolder
		importFolder.setOnAction(a -> {
			File file = Main.specialChooser.selectFolder(Main.window);
			if (file != null)
				inputService.start(Arrays.asList(file));
		});
		
		// importFiles
		importFiles.setOnAction(a -> {
			List<File> list = Main.specialChooser.prepareToImportSongFiles(Main.window);
			if (list != null && !list.isEmpty())
				inputService.start(list);
		});
		
		// exportFiles
		exportFiles.setOnAction(a -> Main.exportWindow.show(this));
		
		// Export
		// ...
		
		// clearAll
		clearAll.setOnAction(ac -> {
			if (ActionTool.doQuestion("You want to remove all the Files from ->" + this + "\n\nThis of course doesn't mean that they will be deleted from your computer",
					Main.window))
				clearDataBaseTable();
		});
		
		//== toolsMenuButton
		toolsMenuButton.setOnMouseReleased(m -> {
			Bounds bounds = toolsMenuButton.localToScreen(toolsMenuButton.getBoundsInLocal());
			toolsContextMenu.show(toolsMenuButton, bounds.getMaxX(), bounds.getMinY());
		});
		
		// -- refreshButton
		//refreshButton.setOnAction(e -> loadService.startService(false, true, false));
		
		//---------------------Check the genre--------------------
		if (genre == Genre.SEARCHWINDOW) {
			navigationHBox.setVisible(false);
			toolsMenuButton.setVisible(false);
			navigationHBox.setManaged(false);
			toolsMenuButton.setManaged(false);
		}
		
		if (genre == Genre.EMOTIONSMEDIA) {
			importFolder.setVisible(false);
			importFiles.setVisible(false);
		}
		
		//------------------------SMART CONTROLLER MODES-----------------
		foldersModeTab.setContent(foldersMode);
		
		//--Change Listener for Modes Tab Pane
		modesTabPane.getSelectionModel().selectedItemProperty().addListener((observable , oldValue , newValue) -> {
			if (newValue == foldersModeTab)
				foldersMode.recreateTree();
		});
		
		//reloadVBox
		reloadVBox.setVisible(false);
		
		//reloadPlayListButton
		reloadPlayListButton.setOnAction(a -> {
			if (isFree(true)) {
				loadService.startService(false, true, false);
				reloadVBox.setVisible(false);
			}
		});
		
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							METHODS
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	
	/**
	 * Prepares the delete operation when more than one Media files will be
	 * deleted.
	 *
	 * @param permanent
	 *            <br>
	 *            true->storage medium + (play list)/library<br>
	 *            false->only from (play list)/library
	 * @param controller
	 *            the controller
	 */
	public void prepareDelete(boolean permanent) {
		int previousTotal = getTotalInDataBase();
		
		// Remove selected items
		removeSelected(permanent);
		
		// Update
		if (previousTotal != getTotalInDataBase()) {
			//	    if (genre == Genre.LIBRARYMEDIA)
			//		Main.libraryMode.multipleLibs.getSelectedLibrary().updateSettingsTotalLabel();
			
			loadService.startService(true, true, true);
		}
	}
	
	/**
	 * Removes the selected songs.
	 *
	 * @param permanent
	 *            <br>
	 *            true->storage medium + (play list)/library false->only from
	 *            (play list)/library<br>
	 */
	private void removeSelected(boolean permanent) {
		
		// Free? && How many items are selected?+Question
		if (!isFree(true))
			return;
		
		List<Boolean> answers = Main.mediaDeleteWindow.doDeleteQuestion(permanent,
				tableViewer.getSelectedCount() != 1 ? Integer.toString(tableViewer.getSelectedCount()) : tableViewer.getSelectionModel().getSelectedItem().getFileName(),
				tableViewer.getSelectedCount(), Main.window);
		
		//Check if the user is sure he want's to go on delete action
		if (!answers.get(0))
			return;
		//Check if the delete will be finally permanent or not
		boolean permanent1 = answers.get(1);
		
		// Remove selected items
		if (genre == Genre.SEARCHWINDOW)
			//Call the delete for each selected item
			tableViewer.getSelectionModel().getSelectedItems().iterator().forEachRemaining(r -> r.delete(permanent1, false, false, this, null));
		else
			try (PreparedStatement preparedDelete = Main.dbManager.getConnection().prepareStatement("DELETE FROM '" + dataBaseTableName + "' WHERE PATH=?")) {
				
				//Call the delete for each selected item
				tableViewer.getSelectionModel().getSelectedItems().iterator().forEachRemaining(r -> r.delete(permanent1, false, false, this, preparedDelete));
				
				// Library?
				//		if (genre == Genre.LIBRARYMEDIA)
				//		    Main.libraryMode.updateLibraryTotalLabel(controllerName);
				
			} catch (Exception ex) {
				Main.logger.log(Level.WARNING, "", ex);
			}
		
	}
	
	/**
	 * Clears all the items from this library *.
	 */
	private void clearDataBaseTable() {
		if (!isFree(true))
			return;
		
		// Security Value
		undepositWorking = true;
		
		// Controller
		getIndicator().setProgress(-1);
		getCancelButton().setText("Clearing...");
		indicatorVBox.setVisible(true);
		
		// New Thread
		new Thread(() -> {
			try {
				Main.dbManager.getConnection().createStatement().executeUpdate("DELETE FROM '" + dataBaseTableName + "'");
				Main.dbManager.commit();
				
				//Check if it is Emotion PlayList so clear the Internal List also babeeee!
				if (genre == Genre.EMOTIONSMEDIA)
					switch (getName()) {
						
						case "HatedMediaPlayList":
							Main.emotionListsController.hatedMediaList.getSet().clear();
							break;
						case "DislikedMediaPlayList":
							Main.emotionListsController.dislikedMediaList.getSet().clear();
							break;
						case "LikedMediaPlayList":
							Main.emotionListsController.likedMediaList.getSet().clear();
							break;
						case "LovedMediaPlayList":
							Main.emotionListsController.lovedMediaList.getSet().clear();
							break;
					}
				
				//Make the Region Disappear in the fog of hell ououou
				Platform.runLater(() -> {
					indicatorVBox.setVisible(false);
					getCancelButton().setText("Cancel");
				});
				
			} catch (Exception ex) {
				Main.logger.log(Level.WARNING, "", ex);
			} finally {
				undepositWorking = false;
			}
		}).start();
		
		simpleClear();
		
		// Library?
		//	    if (genre == Genre.LIBRARYMEDIA)
		//		Main.libraryMode.updateLibraryTotalLabel(controllerName);
	}
	
	private static final String style1 = "-fx-font-weight:bold; -fx-font-size:13; -fx-fill:#FF9000;";
	private static final String style2 = "-fx-font-weight:400; -fx-font-size:13;  -fx-fill:white;";
	
	/**
	 * Updates the label of the smart controller. [[SuppressWarningsSpartan]]
	 */
	public void updateLabel() {
		//System.out.println(this.getName() + " called UpdateLabel()");
		
		//Clear the text area
		detailCssTextArea.clear();
		
		String total = "Total : ";
		String _total = InfoTool.getNumberWithDots(totalInDataBase.get());
		
		String selected = "Selected : ";
		String _selected = String.valueOf(tableViewer.getSelectedCount());
		
		String maxPerPage = "MaxPerPage : ";
		String _maxPerPage = String.valueOf(maximumPerPage);
		
		//Search is Activated?
		if (searchService.isActive() || genre == Genre.SEARCHWINDOW) {
			
			String found = "Found ";
			String _found = String.valueOf(itemsObservableList.size());
			
			//Now set the Text
			appendToDetails(found, _found, true, style1.replace("FF9000", "00BBFF"));
			appendToDetails(total, _total, true, style1.replace("FF9000", "00E148"));
			appendToDetails(selected, _selected, true, style1);
			appendToDetails(maxPerPage, _maxPerPage, false, style1);
			
		} else {
			
			String showing = "Showing : ";
			String _showing = InfoTool.getNumberWithDots(maximumPerPage * currentPage.get()) + " ... "
					+ InfoTool.getNumberWithDots(maximumPerPage * currentPage.get() + itemsObservableList.size());
			
			String maximumPage = "MaximumPage : ";
			String _maximumPage = InfoTool.getNumberWithDots(getMaximumList());
			
			//Now set the Text
			appendToDetails(total, _total, true, style1.replace("FF9000", "00E148"));
			appendToDetails(selected, _selected, true, style1);
			appendToDetails(showing, _showing, true, style1);
			appendToDetails(maxPerPage, _maxPerPage, true, style1);
			appendToDetails(maximumPage, _maximumPage, false, style1);
			
		}
		
		pageField.setText(Integer.toString(currentPage.get()));
	}
	
	/**
	 * This method is used from updateLabel() method to append Text to
	 * detailsCssTextArea
	 * 
	 * @param text1
	 * @param text2
	 * @param appendComma
	 */
	private void appendToDetails(String text1 , String text2 , boolean appendComma , String style1) {
		detailCssTextArea.appendText(text1);
		detailCssTextArea.setStyle(detailCssTextArea.getLength() - text1.length(), detailCssTextArea.getLength() - 1, style1);
		
		detailCssTextArea.appendText(text2 + " " + ( !appendComma ? "" : ", " ));
		detailCssTextArea.setStyle(detailCssTextArea.getLength() - text2.length() - ( appendComma ? 3 : 1 ), detailCssTextArea.getLength() - 1, style2);
	}
	
	/**
	 * Checks if any updates are on progress in the controller.
	 *
	 * @param showMessage
	 *            the show message
	 * @return true->if yes<br>
	 *         false->if not
	 */
	public boolean isFree(boolean showMessage) {
		boolean isFree = true;
		String message = null;
		
		if (depositWorking) {
			isFree = false;
			message = "Depositing";
		} else if (undepositWorking) {
			isFree = false;
			message = "Undepositing";
		} else if (searchService.getService().isRunning()) {
			isFree = false;
			message = "Searching";
		} else if (renameWorking) {
			isFree = false;
			message = "Renaming";
		} else if (updateWorking) {
			isFree = false;
			message = "Updating";
		} else if (copyOrMoveService.isRunning()) {
			isFree = false;
			message = "Copy-Move Service";
		}
		
		if (!isFree && showMessage)
			showMessage(message);
		
		return isFree;
	}
	
	/**
	 * Show message.
	 *
	 * @param reason
	 *            the reason
	 */
	private void showMessage(String reason) {
		ActionTool.showNotification("Message", "[" + reason + "] is working on:\n " + toString() + "\n\t retry as soon as it finish.", Duration.millis(2000),
				NotificationType.INFORMATION);
	}
	
	/**
	 * Unbind.
	 */
	public void unbind() {
		indicatorVBox.visibleProperty().unbind();
		indicatorVBox.setVisible(false);
		indicator.progressProperty().unbind();
	}
	
	/**
	 * Goes on the Previous List.
	 */
	public void goPrevious() {
		if (SmartController.this.genre != Genre.SEARCHWINDOW && isFree(false) && !searchService.isActive() && totalInDataBase.get() != 0 && currentPage.get() > 0) {
			currentPage.set(currentPage.get() - 1);
			loadService.startService(false, true, false);
		}
	}
	
	/**
	 * Goes on the Next List.
	 */
	public void goNext() {
		if (SmartController.this.genre != Genre.SEARCHWINDOW && isFree(false) && !searchService.isActive() && totalInDataBase.get() != 0 && currentPage.get() < getMaximumList()) {
			currentPage.set(currentPage.get() + 1);
			loadService.startService(false, true, false);
		}
	}
	
	/**
	 * Updates the List.
	 */
	public void updateList() {
		
		if (totalInDataBase.get() != 0)
			next.setDisable(! ( currentPage.isEqualTo(0).or(currentPage.lessThan(getMaximumList())).get() && getMaximumList() != 0 ));
		else {
			next.setDisable(true);
			currentPage.set(0);
		}
		
		// update the label
		updateLabel();
		// refresh the tableViewer
		tableViewer.refresh();
		if (!tableViewer.getSortOrder().isEmpty())
			tableViewer.sort();
		
	}
	
	/**
	 * Clear all the items from list.
	 */
	private void simpleClear() {
		itemsObservableList.clear();
		totalInDataBase.set(0);
		updateList();
	}
	
	/**
	 * Calculates the total entries in the database table [it MUST be called
	 * from external thread cause it may lag the application ]
	 */
	public synchronized void calculateTotalEntries() {
		// calculate the total entries
		if (getTotalInDataBase() == 0)
			try (ResultSet s = Main.dbManager.getConnection().createStatement().executeQuery("SELECT COUNT(PATH) FROM '" + getDataBaseTableName() + "';")) {
				
				//Total items
				final int total = s.getInt(1);
				
				//Update the total
				final CountDownLatch latch = new CountDownLatch(1);
				Platform.runLater(() -> {
					setTotalInDataBase(total);
					
					//Count Down
					latch.countDown();
				});
				
				//Wait
				latch.await();
			} catch (SQLException | InterruptedException ex) {
				ex.printStackTrace();
			}
	}
	
	/**
	 * Checks if the Current SmartController database contains the file
	 * 
	 * @param absoluteFilePath
	 *            The AbsoluteFilePath
	 * @return True if contains the file path or false if not
	 */
	public boolean containsFile(String absoluteFilePath) {
		if (genre == Genre.SEARCHWINDOW)
			return false;
		else {
			try (PreparedStatement pStatement = Main.dbManager.getConnection().prepareStatement("SELECT COUNT(PATH) FROM '" + getDataBaseTableName() + "' WHERE PATH=?")) {
				
				//Total items
				pStatement.setString(1, absoluteFilePath);
				return pStatement.executeQuery().getInt(1) > 0;
				
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			return false;
		}
		
	}
	
	@Override
	public String toString() {
		return "PlayList: <" + controllerName + ">";
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							PROPERTIES
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	
	/**
	 * Total in data base property.
	 *
	 * @return the integer property
	 */
	public IntegerProperty totalInDataBaseProperty() {
		return totalInDataBase;
	}
	
	/**
	 * @return the currentPage
	 */
	public IntegerProperty currentPageProperty() {
		return currentPage;
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							SETTERS
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	
	/**
	 * Changes the MaximumPerPage
	 * 
	 * @param newMaximumPerPage
	 * @param updateSmartController
	 *            If true the loadService will start (Memory consuming ;( ) use
	 *            with great care
	 */
	public void setNewMaximumPerPage(int newMaximumPerPage , boolean updateSmartController) {
		if (maximumPerPage == newMaximumPerPage)
			return;
		
		//Change it
		currentPage.set( ( maximumPerPage == 50 ) ? currentPage.get() / 2 : currentPage.get() * 2 + ( currentPage.get() % 2 == 0 ? 0 : 1 ));
		maximumPerPage = newMaximumPerPage;
		reloadVBox.setVisible(true);
		//if (updateSmartController && isFree(false))
		//	loadService.startService(false, true, false);
	}
	
	/**
	 * Sets the name.
	 *
	 * @param newName
	 *            the new name
	 */
	public void setName(String newName) {
		controllerName = newName;
	}
	
	/**
	 * Sets the total in data base.
	 *
	 * @param totalInDataBase
	 *            the new total in data base
	 */
	public void setTotalInDataBase(int totalInDataBase) {
		this.totalInDataBase.set(totalInDataBase);
	}
	
	/**
	 * @param currentPage
	 *            the currentPage to set
	 */
	public void setCurrentPage(IntegerProperty currentPage) {
		this.currentPage = currentPage;
	}
	
	/**
	 * @param informationTextArea
	 *            the informationTextArea to set
	 */
	public void setInformationTextArea(TextArea informationTextArea) {
		this.informationTextArea = informationTextArea;
	}
	
	/**
	 * @param verticalScrollValueWithoutSearch
	 *            the verticalScrollValueWithoutSearch to set
	 */
	public void setVerticalScrollValueWithoutSearch(double verticalScrollValueWithoutSearch) {
		this.verticalScrollValueWithoutSearch = verticalScrollValueWithoutSearch;
	}
	
	/**
	 * @param verticalScrollValueWithSearch
	 *            the verticalScrollValueWithSearch to set
	 */
	public void setVerticalScrollValueWithSearch(double verticalScrollValueWithSearch) {
		this.verticalScrollValueWithSearch = verticalScrollValueWithSearch;
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							GETTERS
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	
	/**
	 * Returns Marked Songs.
	 *
	 * @return the selected items
	 */
	public ObservableList<Media> getSelectedItems() {
		return tableViewer.getSelectionModel().getSelectedItems();
	}
	
	/**
	 * Gets the indicator.
	 *
	 * @return the indicator
	 */
	public ProgressIndicator getIndicator() {
		return indicator;
	}
	
	/**
	 * Gets the cancel button.
	 *
	 * @return the cancel button
	 */
	public Button getCancelButton() {
		return cancelButton;
	}
	
	/**
	 * Gets the next button.
	 *
	 * @return the next button
	 */
	public HBox getNavigationHBox() {
		return navigationHBox;
	}
	
	/**
	 * Gets the next button.
	 *
	 * @return the next button
	 */
	public Button getNextButton() {
		return next;
	}
	
	/**
	 * Gets the previous button.
	 *
	 * @return the previous button
	 */
	public Button getPreviousButton() {
		return previous;
	}
	
	/**
	 * Gets the data base table name.
	 *
	 * @return the data base table name
	 */
	public String getDataBaseTableName() {
		return dataBaseTableName;
	}
	
	/**
	 * Returns the name of the smart controller
	 * 
	 * @return The name of the smartController
	 */
	public String getName() {
		return controllerName;
	}
	
	/**
	 * Return the number of the final List counting from <b>firstList->0
	 * SecondList->1 ....</b>
	 *
	 * @return the int
	 */
	public int getMaximumList() {
		return totalInDataBase.get() == 0 ? 0 : ( totalInDataBase.get() / maximumPerPage ) + ( ( totalInDataBase.get() % maximumPerPage == 0 ) ? -1 : 0 );
	}
	
	/**
	 * @return The Vertical ScrollBar of TableViewer
	 */
	public Optional<ScrollBar> getVerticalScrollBar() {
		
		return Optional.ofNullable((ScrollBar) getTableViewer().lookup(".scroll-bar:vertical"));
	}
	
	/**
	 * @return the centerStackPane
	 */
	public StackPane getCenterStackPane() {
		return centerStackPane;
	}
	
	/**
	 * @return the searchService
	 */
	public SmartControllerSearcher getSearchService() {
		return searchService;
	}
	
	/**
	 * @return the loadService
	 */
	public LoadService getLoadService() {
		return loadService;
	}
	
	/**
	 * @return the inputService
	 */
	public InputService getInputService() {
		return inputService;
	}
	
	/**
	 * @return the copyOrMoveService
	 */
	public FilesExportService getCopyOrMoveService() {
		return copyOrMoveService;
	}
	
	/**
	 * @return the maximumPerPage
	 */
	public int getMaximumPerPage() {
		return maximumPerPage;
	}
	
	/**
	 * @return the indicatorVBox
	 */
	public VBox getIndicatorVBox() {
		return indicatorVBox;
	}
	
	/**
	 * @return the genre
	 */
	public Genre getGenre() {
		return genre;
	}
	
	/**
	 * @return the tableViewer
	 */
	public MediaTableViewer getTableViewer() {
		return tableViewer;
	}
	
	/**
	 * @return the itemsObservableList
	 */
	public ObservableList<Media> getItemsObservableList() {
		return itemsObservableList;
	}
	
	/**
	 * @return the informationTextArea
	 */
	public TextArea getInformationTextArea() {
		return informationTextArea;
	}
	
	/**
	 * Gets the total in data base.
	 *
	 * @return the total in data base
	 */
	public int getTotalInDataBase() {
		return totalInDataBase.get();
	}
	
	/**
	 * @return the verticalScrollValueWithoutSearch
	 */
	public double getVerticalScrollValueWithoutSearch() {
		return verticalScrollValueWithoutSearch;
	}
	
	/**
	 * @return the verticalScrollValueWithSearch
	 */
	public double getVerticalScrollValueWithSearch() {
		return verticalScrollValueWithSearch;
	}
	
	/**
	 * @return the toolsContextMenu
	 */
	public ContextMenu getToolsContextMenu() {
		return toolsContextMenu;
	}
	
	/**
	 * @return the instantSearch
	 */
	public JFXCheckBox getInstantSearch() {
		return instantSearch;
	}
	
	/**
	 * @return the reloadVBox
	 */
	public VBox getReloadVBox() {
		return reloadVBox;
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							RUBBISH CODE
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	
	//	/**
	//	 * Indicates that a capture event has been fired to this controller from the
	//	 * CaptureWindow.
	//	 *
	//	 * @param array
	//	 *        the array
	//	 */
	//	public void fireCaptureEvent(int[] array) {
	//		// Bounds bounds
	//		/*
	//		 * for (ButtonBase song : bigList) { bounds =
	//		 * song.localToScreen(song.getBoundsInLocal()); // button rectangle
	//		 * overlaps capture rectangle ? if (bounds.getMinX() <= array[0] +
	//		 * array[2] && bounds.getMaxX() >= array[0] && bounds.getMinY() <=
	//		 * array[1] + array[3] && bounds.getMaxY() >= array[1]) ((Audio)
	//		 * song).setMarked(true, false); else ((Audio) song).setMarked(false,
	//		 * false); }
	//		 */
	//		
	//		updateLabel();
	//	}
	
}
