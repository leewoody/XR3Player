/*
 * 
 */
package main.java.com.goxr3plus.xr3player.application.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.Tab;
import javafx.util.Duration;
import main.java.com.goxr3plus.xr3player.application.Main;
import main.java.com.goxr3plus.xr3player.application.modes.librarymode.Library;
import main.java.com.goxr3plus.xr3player.application.modes.loginmode.User;
import main.java.com.goxr3plus.xr3player.application.settings.ApplicationSettingsLoader;
import main.java.com.goxr3plus.xr3player.application.tools.ActionTool;
import main.java.com.goxr3plus.xr3player.application.tools.ActionTool.FileType;
import main.java.com.goxr3plus.xr3player.application.tools.InfoTool;
import main.java.com.goxr3plus.xr3player.application.tools.NotificationType;
import main.java.com.goxr3plus.xr3player.smartcontroller.presenter.SmartController;
import main.java.com.goxr3plus.xr3player.smartcontroller.services.Operation;

/**
 * This class is managing the database of the application.
 *
 * @author GOXR3PLUS
 */
public class DbManager {
	
	/** The connection 1. */
	private Connection connection;
	
	/** The data loader. */
	private DataLoader dataLoader = new DataLoader();
	
	/** This executor does the commit job. */
	private final ExecutorService commitExecutor = Executors.newSingleThreadExecutor();
	
	/** If true -> The database notifications are shown */
	private static final boolean SHOWNOTIFICATIONS = false;
	
	//----------------------
	
	/**
	 * The KeyValueDb
	 */
	//private JSONDB keyValueDb = new JSONDB(this)
	
	/**
	 * Here are stored all the settings for the user account
	 */
	private PropertiesDb userSettingsDb;
	
	//-------------------------
	
	/** The runnable of the commit executor. */
	private Runnable commitRunnable = () -> {
		try {
			connection.commit();
		} catch (SQLException ex) {
			Main.logger.log(Level.WARNING, ex.getMessage(), ex);
		} finally {
			if (SHOWNOTIFICATIONS)
				ActionTool.showNotification("Commited", "Changes saved successfully", Duration.millis(150), NotificationType.INFORMATION);
		}
		
	};
	
	/** The runnable of the commit executor. */
	private Runnable vacuumRunnable = () -> {
		try {
			if (connection != null) {
				// close + open connection
				connection.commit();
				manageConnection(Operation.CLOSE);
				manageConnection(Operation.OPEN);
				
				// vacuum
				connection.createStatement().executeUpdate("VACUUM");
				
				// close connection
				manageConnection(Operation.CLOSE);
			}
			// exit
			System.exit(0);
		} catch (SQLException ex) {
			Main.logger.log(Level.WARNING, ex.getMessage(), ex);
			System.exit(-1);
		} finally {
			System.exit(0);
		}
		
	};
	
	//-----------------------------------------------------------
	
	/**
	 * 
	 *
	 * @param userName
	 *            the user name
	 */
	public void initialize(String userName) {
		
		// Initialise
		InfoTool.setUserName(userName);
		
		//Create the settingsFolder
		File settingsFolder = new File(InfoTool.getUserFolderAbsolutePathWithSeparator() + "settings");
		ActionTool.createFileOrFolder(settingsFolder, FileType.DIRECTORY);
		
		//Create the propertiesDb
		userSettingsDb = new PropertiesDb(settingsFolder + File.separator + InfoTool.USER_SETTINGS_FILE_NAME, true);
		
		// User Folder
		File userFolder = new File(InfoTool.getAbsoluteDatabasePathWithSeparator() + userName);
		ActionTool.createFileOrFolder(userFolder, FileType.DIRECTORY);
		
		// Images Folder
		File imagesFolder = new File(InfoTool.getImagesFolderAbsolutePathPlain());
		ActionTool.createFileOrFolder(imagesFolder, FileType.DIRECTORY);
		
		//XPlayer Images Folder
		File xPlayerImagesFolder = new File(InfoTool.getXPlayersImageFolderAbsolutePathPlain());
		ActionTool.createFileOrFolder(xPlayerImagesFolder, FileType.DIRECTORY);
		
		//Attempt DataBase connection
		try {
			String dbFileAbsolutePath = InfoTool.getUserFolderAbsolutePathWithSeparator() + "dbFile.db";
			connection = DriverManager.getConnection("jdbc:sqlite:" + dbFileAbsolutePath);
			connection.setAutoCommit(false);
			
			//if (!data1Exist)
			
			//This method keeps backward compatibility with previous XR3Player versions
			createDatabaseMissingTables();
			
			//keyValueDb.recreateJSonDataBase();
			
		} catch (SQLException ex) {
			Main.logger.log(Level.SEVERE, "", ex);
		}
		
	}
	
	//------------------- Getters ---------------------------------------------------------------
	
	/**
	 * @return the propertiesDb
	 */
	public PropertiesDb getPropertiesDb() {
		return userSettingsDb;
	}
	
	/**
	 * @return the showNotifications
	 */
	public boolean isShowNotifications() {
		return SHOWNOTIFICATIONS;
	}
	
	public Connection getConnection() {
		return connection;
	}
	
	//------------------- Setters --------------------------------------------------------------
	
	//------------------- Methods --------------------------------------------------------------
	
	/**
	 * Open or close the connection.
	 *
	 * @param action
	 *            the action
	 */
	public void manageConnection(Operation action) {
		try {
			// OPEN
			if (action == Operation.OPEN && connection.isClosed())
				connection = DriverManager.getConnection("jdbc:sqlite:" + InfoTool.getUserFolderAbsolutePathWithSeparator() + "dbFile.db");
			// CLOSE
			else if (action == Operation.CLOSE && connection != null && !connection.isClosed())
				connection.close();
		} catch (SQLException ex) {
			Main.logger.log(Level.WARNING, "", ex);
		}
	}
	
	/**
	 * Using this methods to control commits across the application so not to
	 * have unexpected lags.
	 */
	public void commit() {
		commitExecutor.execute(commitRunnable);
	}
	
	/**
	 * Using this methods to control commit + vacuum across the application so
	 * not to have unexpected lags.
	 * 
	 */
	public void commitAndVacuum() {
		commitExecutor.execute(vacuumRunnable);
	}
	
	/**
	 * Stops the executorService.
	 */
	public void shutdownCommitExecutor() {
		commitExecutor.shutdown();
	}
	
	/**
	 * Checks if this table exists in the SQL DataBase
	 *
	 * @param tableName
	 *            the table name
	 * @return true, if successful
	 */
	public boolean doesTableExist(String tableName) {
		// SQLite table names are case insensitive, but comparison is case
		// sensitive by default. To make this work properly in all cases you
		// need to add COLLATE NOCASE
		try (ResultSet r = connection.createStatement().executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tableName + "' COLLATE NOCASE ")) {
			int total = r.getInt(1);
			return total == 0 ? false : true;
		} catch (SQLException ex) {
			Main.logger.log(Level.INFO, "", ex);
		}
		
		return false;
	}
	
	/**
	 * Recreates the database if it doesn't exist.
	 */
	public void createDatabaseMissingTables() {
		
		Main.logger.info("1-->Checking for missing database tables");
		
		try (Statement statement = connection.createStatement()) {
			
			// ----------Libraries Table ----------------//
			statement.executeUpdate(
					"CREATE TABLE IF NOT EXISTS LIBRARIES (NAME          TEXT    PRIMARY KEY   NOT NULL," + "TABLENAME TEXT NOT NULL," + "STARS         DOUBLE     NOT NULL,"
							+ "DATECREATED          TEXT   	NOT NULL," + "TIMECREATED          TEXT    NOT NULL," + "DESCRIPTION   TEXT    NOT NULL,"
							+ "SAVEMODE      INT     NOT NULL," + "POSITION      INT     NOT NULL," + "LIBRARYIMAGE  TEXT," + "OPENED BOOLEAN NOT NULL )");
			
			// -----------Radio Stations Table ------------//
			//  statement.executeUpdate("CREATE TABLE '" + InfoTool.RADIO_STATIONS_DATABASE_TABLE_NAME + "'(NAME TEXT PRIMARY KEY NOT NULL,"
			//	    + "STREAMURL TEXT NOT NULL," + "TAGS TEXT NOT NULL," + "DESCRIPTION TEXT," + "STARS DOUBLE NOT NULL)");
			
			// ----------XPlayers PlayLists Tables ----------//
			for (int i = 0; i < 3; i++)
				createXPlayListTable(statement, i);
			
			commit();
		} catch (SQLException ex) {
			Main.logger.log(Level.SEVERE, "", ex);
		}
	}
	
	/**
	 * Create a database table for the specific XPlayer.
	 *
	 * @param statement
	 *            the statement
	 * @param key
	 *            the key
	 * @throws SQLException
	 *             the SQL exception
	 */
	private void createXPlayListTable(Statement statement , int key) throws SQLException {
		statement.executeUpdate("CREATE TABLE IF NOT EXISTS `XPPL" + key
				+ "` (PATH TEXT PRIMARY KEY  NOT NULL , STARS  DOUBLE  NOT NULL , TIMESPLAYED  INT NOT NULL , DATE TEXT NOT NULL , HOUR TEXT NOT NULL);");
	}
	
	/**
	 * This method loads the application database.
	 */
	public void loadApplicationDataBase() {
		Main.updateScreen.setVisible(true);
		Main.updateScreen.getProgressBar().progressProperty().bind(dataLoader.progressProperty());
		dataLoader.restart();
	}
	
	//-------------------------------------------------------------------
	public Optional<User> getOpenedUser() {
		return Main.loginMode.teamViewer.getItemsObservableList().stream().filter(user -> user.getUserName().equals(InfoTool.getUserName())).findFirst();
	}
	
	/**
	 * Loads all [ Opened-Libraries ] and the [ Last-Opened-Library ] as
	 * properties from the UserInformation.properties file
	 * [[SuppressWarningsSpartan]]
	 */
	public void loadOpenedLibraries() {
		
		//Get the current User
		getOpenedUser().ifPresent(user -> {
			
			//Load the properties
			Properties properties = user.getUserInformationDb().loadProperties();
			
			//Load the opened libraries
			//			Optional.ofNullable(properties.getProperty("Opened-Libraries")).ifPresent(openedLibraries -> {
			//				
			//				//Use the split to get all the Opened Libraries Names
			//				Arrays.asList(openedLibraries.split("\\<\\|\\>\\:\\<\\|\\>")).stream().forEach(name -> {
			//					Platform.runLater(() -> {
			//						//System.out.println(name); //debugging
			//						
			//						//Get the Library and Open it!
			//						Main.libraryMode.getLibraryWithName(name).get().libraryOpenClose(true, true);
			//					});
			//				});
			//			});
			
			//Load all the Opened Libraries
			Platform.runLater(
					() -> Main.libraryMode.teamViewer.getViewer().getItemsObservableList().stream().filter(Library::isOpened).forEach(library -> library.openLibrary(true, true)));
			
			//Add Selection Model ChangeListener 
			Platform.runLater(() -> {
				Main.libraryMode.multipleLibs.getTabPane().getSelectionModel().selectedItemProperty().addListener((observable , oldTab , newTab) -> {
					
					// Give a refresh to the newly selected ,!! ONLY IF IT HAS NO ITEMS !! 
					if (!Main.libraryMode.multipleLibs.getTabPane().getTabs().isEmpty() && ( (SmartController) newTab.getContent() ).isFree(false)
							&& ( (SmartController) newTab.getContent() ).getItemsObservableList().isEmpty()) {
						
						( (SmartController) newTab.getContent() ).getLoadService().startService(false, true, false);
						
						storeOpenedLibraries();
					}
					
					//System.out.println("Changed...");
					storeLastOpenedLibrary();
				});
				
				//Load the Last Opened Library
				Optional.ofNullable(properties.getProperty("Last-Opened-Library")).ifPresent(lastOpenedLibrary -> {
					
					//Select the correct library inside the TabPane
					Main.libraryMode.multipleLibs.getTabPane().getSelectionModel().select(Main.libraryMode.multipleLibs.getTab(lastOpenedLibrary));
					
					//This will change in future update when user can change the default position of Libraries
					Main.libraryMode.teamViewer.getViewer().setCenterIndex(Main.libraryMode.multipleLibs.getSelectedLibrary().get().getPosition());
					
				});
				
				//Update last selected Library SmartController if not empty
				Main.libraryMode.multipleLibs.getSelectedLibrary().ifPresent(selectedLibrary -> {
					if (selectedLibrary.getSmartController().isFree(false))
						selectedLibrary.getSmartController().getLoadService().startService(false, true, false);
				});
			});
		});
		
	}
	
	/**
	 * Stores the last opened library - That means the library that was selected
	 * on the Multiple Libraries Tab Pane <br>
	 * !Must be called from JavaFX Thread!
	 */
	private void storeLastOpenedLibrary() {
		
		//Get the current User
		getOpenedUser().ifPresent(user -> {
			
			ObservableList<Tab> openedLibrariesTabs = Main.libraryMode.multipleLibs.getTabs();
			
			//Save the last opened library
			if (openedLibrariesTabs.isEmpty()) {
				///System.out.println("Last-Opened-Library is Empty");
				user.getUserInformationDb().deleteProperty("Last-Opened-Library");
			} else {
				Tab tab = Main.libraryMode.multipleLibs.getTabPane().getSelectionModel().getSelectedItem();
				//System.out.println("Last-Opened-Library: " + tab.getTooltip().getText());
				user.getUserInformationDb().updateProperty("Last-Opened-Library", tab.getTooltip().getText());
			}
		});
	}
	
	/**
	 * Stores all the opened libraries and the last selected one as properties
	 * to the UserInformation.properties file <br>
	 * !Must be called from JavaFX Thread!
	 * 
	 * @param openedLibrariesTabs
	 */
	public void storeOpenedLibraries() {
		
		//Get the opened user and store the opened libraries
		getOpenedUser().ifPresent(user -> {
			ObservableList<Tab> openedLibrariesTabs = Main.libraryMode.multipleLibs.getTabs();
			
			//			//Save the opened libraries
			//			if (openedLibrariesTabs.isEmpty())
			//				user.getUserInformationDb().deleteProperty("Opened-Libraries");
			//			else {
			//				
			//				//Join all library names to a string using as separator char "<|>:<|>"
			//				String openedLibs = openedLibrariesTabs.stream().map(tab -> tab.getTooltip().getText()).collect(Collectors.joining("<|>:<|>"));
			//				user.getUserInformationDb().updateProperty("Opened-Libraries", openedLibs);
			//				
			//				//System.out.println("Opened Libraries:\n-> " + openedLibs); //debugging
			//			}
			
			//Save the last opened library
			storeLastOpenedLibrary();
		});
		
	}
	
	/**
	 * DataLoader.
	 *
	 * @author GOXR3PLUS STUDIO
	 */
	public class DataLoader extends Service<Void> {
		
		/** The total. */
		private int totalLibraries;
		
		/**
		 * Constructor.
		 */
		public DataLoader() {
			
			// -------------------if succeeded
			setOnSucceeded(s -> {
				
				//----------------Do the animation with rectangles---------------------
				Main.updateScreen.closeUpdateScreen();
				
				//----------------Finall Settings---------------------
				// update library viewer
				Main.libraryMode.teamViewer.getViewer().update();
				//Main.libraryMode.libraryViewer.goOnSelectionMode(false)
				
				// set libraries tree expanded
				//Main.treeManager.librariesTree.setExpanded(true)
				
				//---------------Set the update Screen invisible---------------------
				PauseTransition pause1 = new PauseTransition(Duration.seconds(1));
				pause1.setOnFinished(f -> {
					Main.updateScreen.setVisible(false);
					Main.updateScreen.getProgressBar().progressProperty().unbind();
				});
				pause1.playFromStart();
				
			});
			
			// ---------------------if failed
			setOnFailed(fail -> {
				Main.updateScreen.getProgressBar().progressProperty().unbind();
				ActionTool.showNotification("Fatal Error!", "DataLoader failed during loading dataBase!!Application will exit...", Duration.millis(1500), NotificationType.ERROR);
				System.exit(0);
			});
		}
		
		@Override
		protected Task<Void> createTask() {
			return new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					int totalSteps = 4;
					
					// -------------------------- Load all the libraries
					try (ResultSet resultSet = getConnection().createStatement().executeQuery("SELECT* FROM LIBRARIES;");
							ResultSet dbCounter = getConnection().createStatement().executeQuery("SELECT COUNT(NAME) FROM LIBRARIES;");) {
						
						totalLibraries = dbCounter.getInt(1);
						
						// Refresh the text
						Platform.runLater(() -> Main.updateScreen.getLabel().setText("Loading Libraries....[ 0 / " + totalLibraries + " ]"));
						
						//Kepp a List of all Libraries
						final List<Library> libraries = new ArrayList<>(totalLibraries);
						int[] counter = { 0 };
						
						// Load all the libraries
						while (resultSet.next()) {
							//Add the library to the List of Libraries
							libraries.add(new Library(resultSet.getString("NAME"), resultSet.getString("TABLENAME"), resultSet.getDouble("STARS"),
									resultSet.getString("DATECREATED"), resultSet.getString("TIMECREATED"), resultSet.getString("DESCRIPTION"), resultSet.getInt("SAVEMODE"),
									resultSet.getInt("POSITION"), resultSet.getString("LIBRARYIMAGE"), resultSet.getBoolean("OPENED")));
							
							//Change Label Text
							++counter[0];
							Platform.runLater(() -> Main.updateScreen.getLabel().setText("Loading Libraries... [ " + counter[0] + " / " + totalLibraries + " ]"));
							
						}
						
						//Update the Progress
						//updateProgress(1, totalSteps);
						
						//Run of JavaFX Thread
						Platform.runLater(() -> {
							
							//Change Label Text
							Main.updateScreen.getLabel().setText("Adding Libraries ...");
							
							//Add all the Libraries to the Library Viewer
							Main.libraryMode.teamViewer.getViewer().addMultipleItems(libraries);
							
							//Change Label Text
							Main.updateScreen.getLabel().setText("Loading Opened Libraries...");
						});
						
						//Update the Progress
						//updateProgress(2, totalSteps);
						
						//Load the Opened Libraries
						loadOpenedLibraries();
						
						//Run of JavaFX Thread
						Platform.runLater(() -> {
							
							//Calculate Opened Libraries
							Main.libraryMode.calculateOpenedLibraries();
							
							//Calculate Empty Libraries
							Main.libraryMode.calculateEmptyLibraries();
							
						});
						
						//Load PlayerMediaList
						Platform.runLater(() -> Main.updateScreen.getLabel().setText("Loading database data..."));
						Main.playedSongs.uploadFromDataBase();
						Main.emotionListsController.hatedMediaList.uploadFromDataBase();
						Main.emotionListsController.dislikedMediaList.uploadFromDataBase();
						Main.emotionListsController.likedMediaList.uploadFromDataBase();
						Main.emotionListsController.lovedMediaList.uploadFromDataBase();
						
						//Update the Progress
						//updateProgress(3, totalSteps);
						
						Platform.runLater(() -> {
							
							//Change Label Text
							Platform.runLater(() -> Main.updateScreen.getLabel().setText("Loading User Settings Part ( 1 )..."));
							
							//Update the emotion smart controller 
							Main.emotionListsController.updateEmotionSmartControllers(true, true, true, true);
							
							//Refresh all the XPlayers PlayLists
							Main.xPlayersList.getList().stream()
									.forEach(xPlayerController -> xPlayerController.getxPlayerPlayList().getSmartController().getLoadService().startService(false, false, false));
							
							//------Bind Instant Search between all SmartControllers
							
							//For Search Window
							Main.searchWindowSmartController.getInstantSearch().selectedProperty()
									.bindBidirectional(Main.settingsWindow.getPlayListsSettingsController().getInstantSearch().selectedProperty());
							
							//For Libraries
							Main.libraryMode.teamViewer.getViewer().getItemsObservableList().stream().map(Library::getSmartController)
									.forEach(controller -> controller.getInstantSearch().selectedProperty()
											.bindBidirectional(Main.settingsWindow.getPlayListsSettingsController().getInstantSearch().selectedProperty()));
							
							//For XPLayersLists
							Main.xPlayersList.getList().stream().forEach(xPlayerController -> xPlayerController.getxPlayerPlayList().getSmartController().getInstantSearch()
									.selectedProperty().bindBidirectional(Main.settingsWindow.getPlayListsSettingsController().getInstantSearch().selectedProperty()));
							
							//Change Label Text
							Platform.runLater(() -> Main.updateScreen.getLabel().setText("Loading User Settings Part ( 2 )..."));
							
							//-----------------------Load the application settings-------------------------------					
							ApplicationSettingsLoader.loadApplicationSettings();
							
						});
						
						//Update the Progress
						//updateProgress(4, 4);
						
					} catch (Exception ex) {
						Main.logger.log(Level.SEVERE, "", ex);
					}
					
					return null;
				}
			};
		}
		
	}
	
}
