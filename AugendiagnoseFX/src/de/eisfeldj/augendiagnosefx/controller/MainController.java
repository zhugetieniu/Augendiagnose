package de.eisfeldj.augendiagnosefx.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import de.eisfeldj.augendiagnosefx.util.DialogUtil;
import de.eisfeldj.augendiagnosefx.util.DialogUtil.ConfirmDialogListener;
import de.eisfeldj.augendiagnosefx.util.FxmlUtil;
import de.eisfeldj.augendiagnosefx.util.Logger;
import de.eisfeldj.augendiagnosefx.util.PreferenceUtil;
import de.eisfeldj.augendiagnosefx.util.ResourceConstants;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import static de.eisfeldj.augendiagnosefx.util.PreferenceUtil.KEY_SHOW_COMMENT_PANE;
import static de.eisfeldj.augendiagnosefx.util.PreferenceUtil.KEY_SHOW_SPLIT_WINDOW;

/**
 * The controller of the main window.
 */
public class MainController extends BaseController implements Initializable {
	/**
	 * The root of the main page.
	 */
	@FXML
	private VBox mMainPane;

	/**
	 * The pane containing the body.
	 */
	@FXML
	private StackPane mBody;

	/**
	 * The pane containing the menu bar.
	 */
	@FXML
	private MenuBar mMenuBar;

	/**
	 * The pane containing menu buttons.
	 */
	@FXML
	private HBox mMenuButtons;

	/**
	 * The onepane/twopane button in the menu bar.
	 */
	@FXML
	private ToggleButton mPaneButton;

	/**
	 * The comment button in the menu bar.
	 */
	@FXML
	private ToggleButton mCommentButton;

	/**
	 * The close button in the menu bar.
	 */
	@FXML
	private Button mCloseButton;

	/**
	 * The save icon.
	 */
	@FXML
	private ImageView mImageSave;

	/**
	 * The panes (one or two) containing the body.
	 */
	private StackPane[] mBodies = new StackPane[0];

	/**
	 * The list of subpages.
	 */
	private List<BaseController> mSubPageRegistry = new ArrayList<>();

	/**
	 * A list storing the handlers for closing windows.
	 */
	private List<EventHandler<ActionEvent>> mCloseHandlerList = new ArrayList<>();

	/**
	 * Indicator if two panes are shown.
	 */
	private boolean mIsSplitPane = false;

	/**
	 * Get information if two panes are shown.
	 *
	 * @return True if two panes are shown.
	 */
	public final boolean isSplitPane() {
		return getInstance().mIsSplitPane;
	}

	/**
	 * Set split pane.
	 *
	 * @param initialFill
	 *            The FXML file defining the initial content of the new pane.
	 */
	public final void setSplitPane(final String initialFill) {
		if (mIsSplitPane) {
			return;
		}
		mIsSplitPane = true;

		StackPane body1 = new StackPane();
		StackPane body2 = new StackPane();

		SplitPane splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.HORIZONTAL);
		splitPane.getItems().add(body1);
		splitPane.getItems().add(body2);

		mBodies = new StackPane[] {body1, body2, mBody};

		if (initialFill != null) {
			FxmlUtil.displaySubpage(initialFill, 1, false);
		}

		// put lowest two elements to left pane.
		ObservableList<Node> children = mBody.getChildren();
		if (children.size() > 0) {
			body1.getChildren().addAll(children.subList(0, Math.min(2, children.size())));
		}
		// put other elements to right pane.
		if (children.size() > 0) {
			body2.getChildren().addAll(children);
		}

		children.clear();
		children.add(splitPane);

		refreshSubPagesOnResize();
	}

	/**
	 * Set single pane.
	 */
	public final void setSinglePane() {
		if (!mIsSplitPane) {
			return;
		}
		mIsSplitPane = false;

		mBody.getChildren().clear();

		List<BaseController> controllersUncloseable = new ArrayList<>();
		List<BaseController> controllersPane0 = new ArrayList<>();
		List<BaseController> controllersPane1 = new ArrayList<>();
		for (BaseController controller : mSubPageRegistry) {
			if (controller.getPaneIndex() == 0) {
				if (controller.isCloseable()) {
					controllersPane0.add(controller);
				}
				else {
					controllersUncloseable.add(controller);
				}
			}
			else {
				if (controller.isCloseable()) {
					controllersPane1.add(controller);
				}
			}
		}
		for (int i = mSubPageRegistry.size() - 1; i >= 0; i--) {
			removeSubpage(mSubPageRegistry.get(i));
		}

		mBodies = new StackPane[] {mBody};

		for (BaseController controller : controllersUncloseable) {
			addSubPage(controller, 0, false);
			controller.addToRegistry();
		}
		for (BaseController controller : controllersPane0) {
			addSubPage(controller, 0, true);
			controller.addToRegistry();
		}
		for (BaseController controller : controllersPane1) {
			addSubPage(controller, 0, true);
			controller.addToRegistry();
		}

		refreshSubPagesOnResize();
	}

	/**
	 * Handler for pane button.
	 *
	 * @param event
	 *            The action event.
	 */
	@FXML
	public final void toggleSplitWindow(final ActionEvent event) {
		MenuController.getInstance().toggleSplitWindow(event);
	}

	/**
	 * Handler for comment button.
	 *
	 * @param event
	 *            The action event.
	 */
	@FXML
	public final void toggleCommentPane(final ActionEvent event) {
		MenuController.getInstance().toggleCommentPane(event);
	}

	/**
	 * Refresh all subpages on resize.
	 */
	private void refreshSubPagesOnResize() {
		for (BaseController controller : mSubPageRegistry) {
			controller.refreshOnResize();
		}
	}

	@Override
	public final Parent getRoot() {
		return mMainPane;
	}

	@Override
	public final void initialize(final URL location, final ResourceBundle resources) {
		mBodies = new StackPane[] {mBody};
		mPaneButton.setSelected(PreferenceUtil.getPreferenceBoolean(KEY_SHOW_SPLIT_WINDOW));
		mCommentButton.setSelected(PreferenceUtil.getPreferenceBoolean(KEY_SHOW_COMMENT_PANE));
	}

	/**
	 * Get the main controller instance.
	 *
	 * @return The main controller instance.
	 */
	public static MainController getInstance() {
		try {
			return getController(MainController.class);
		}
		catch (TooManyControllersException | MissingControllerException e) {
			Logger.error("Could not find main controller", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Set the contents of the menu bar.
	 *
	 * @param menuBarContents
	 *            The contents of the menu bar.
	 */
	public final void setMenuBarContents(final MenuBar menuBarContents) {
		mMenuBar.getMenus().clear();
		mMenuBar.getMenus().addAll(menuBarContents.getMenus());
	}

	/**
	 * Add a subpage.
	 *
	 * @param controller
	 *            The controller of the subpage.
	 * @param paneIndex
	 *            The pane where to add the subpage.
	 * @param isCloseable
	 *            Indicator if this is a closable page.
	 */
	public final void addSubPage(final BaseController controller, final int paneIndex, final boolean isCloseable) {
		int effectivePaneIndex = paneIndex;
		if (paneIndex == -1) {
			effectivePaneIndex = isSplitPane() ? 2 : 0;
		}

		mBodies[effectivePaneIndex].getChildren().add(controller.getRoot());
		controller.setPaneIndex(effectivePaneIndex);
		controller.setCloseable(isCloseable);

		int position;
		if (isCloseable) {
			position = mSubPageRegistry.size();
			mSubPageRegistry.add(controller);
		}
		else {
			position = unclosablePagesCount();
			mSubPageRegistry.add(position, controller);
		}

		// Enable the close menu.
		enableClose(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				if (controller.isDirty()) {
					ConfirmDialogListener listener = new ConfirmDialogListener() {
						@Override
						public void onDialogPositiveClick() {
							controller.setDirty(false);
							MainController.this.removeSubpage(controller);
						}

						@Override
						public void onDialogNegativeClick() {
							// do nothing.
						}
					};
					DialogUtil.displayConfirmationMessage(listener, ResourceConstants.BUTTON_OK,
							ResourceConstants.MESSAGE_CONFIRM_EXIT_UNSAVED);
				}
				else {
					MainController.this.removeSubpage(controller);
				}
			}
		}, position);

		updatePaneButtonVisibility();
	}

	/**
	 * Remove a subpage.
	 *
	 * @param controller
	 *            The controller of the subpage.
	 */
	private void removeSubpage(final BaseController controller) {
		controller.close();
		int index = mSubPageRegistry.indexOf(controller);
		mSubPageRegistry.remove(controller);
		disableClose(index);
		updatePaneButtonVisibility();

		if (MainController.getInstance().isSplitPane() && !hasClosablePage()) {
			MainController.getInstance().setSinglePane();
		}
	}

	/**
	 * Remove all subpages.
	 */
	public final void removeAllSubPages() {
		for (BaseController controller : mSubPageRegistry) {
			controller.close();
		}
		mSubPageRegistry.clear();
		disableAllClose();
		updatePaneButtonVisibility();
	}

	/**
	 * Enable the close menu item.
	 *
	 * @param eventHandler
	 *            The event handler to be called when closing.
	 * @param position
	 *            The position in the stack. (-1 means end)
	 */
	private void enableClose(final EventHandler<ActionEvent> eventHandler, final int position) {
		if (position >= 0) {
			mCloseHandlerList.add(position, eventHandler);
		}
		else {
			mCloseHandlerList.add(eventHandler);
		}
		if (hasClosablePage()) {
			MenuController.getInstance().setMenuClose(true, eventHandler);

			mCloseButton.setVisible(true);
			mCloseButton.setOnAction(mCloseHandlerList.get(mCloseHandlerList.size() - 1));
		}
	}

	/**
	 * Disable one level of the close menu item.
	 *
	 * @param position
	 *            The position in the stack. (-1 means end)
	 */
	private void disableClose(final int position) {
		if (position >= 0) {
			mCloseHandlerList.remove(position);
		}
		else {
			mCloseHandlerList.remove(mCloseHandlerList.size() - 1);
		}
		if (hasClosablePage()) {
			EventHandler<ActionEvent> newEventHandler = mCloseHandlerList.get(mCloseHandlerList.size() - 1);
			MenuController.getInstance().setMenuClose(true, newEventHandler);
			mCloseButton.setOnAction(newEventHandler);
		}
		else {
			MenuController.getInstance().setMenuClose(false, null);
			mCloseButton.setVisible(false);
		}
	}

	/**
	 * Check if there is a page that can be closed.
	 *
	 * @return true if there is a page that can be closed.
	 */
	public final boolean hasClosablePage() {
		return mSubPageRegistry.size() > unclosablePagesCount();
	}

	/**
	 * Get the number of unclosable pages.
	 *
	 * @return the number of unclosable pages.
	 */
	private int unclosablePagesCount() {
		int counter = 0;
		for (BaseController controller : mSubPageRegistry) {
			if (!controller.isCloseable()) {
				counter++;
			}
		}
		return counter;
	}

	/**
	 * Disable all levels of the close menu icon.
	 */
	private void disableAllClose() {
		mCloseHandlerList.clear();
		MenuController.getInstance().setMenuClose(false, null);
		mCloseButton.setVisible(false);
	}

	/**
	 * Set the save icon visibility.
	 *
	 * @param visible the target visibility
	 */
	public static void setSaveIconVisibility(final boolean visible) {
		getInstance().mImageSave.setVisible(visible);
	}

	/**
	 * Find out if there is any dirty page that requires saving.
	 *
	 * @return true if there is a dirty page.
	 */
	public static boolean hasDirtyBaseController() {
		for (BaseController controller : getInstance().mSubPageRegistry) {
			if (controller.isDirty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Set the status of the pane button.
	 *
	 * @param twopane true if it should display twopane icon.
	 */
	protected void setPaneButtonStatus(final boolean twopane) {
		mPaneButton.setSelected(twopane);
	}

	/**
	 * Set the status of the comment button.
	 *
	 * @param commentPane true if it should display active comment pane icon.
	 */
	protected void setCommentButtonStatus(final boolean commentPane) {
		mCommentButton.setSelected(commentPane);
	}

	/**
	 * Set the visibility of the comment button.
	 *
	 * @param visible true if it should be visible.
	 */
	protected void setCommentButtonVisibility(final boolean visible) {
		mCommentButton.setVisible(visible);
	}

	/**
	 * Update the visibility of the pane button.
	 */
	private void updatePaneButtonVisibility() {
		boolean enabled = BaseController.getControllers(DisplayImagePairController.class).size() == 0
				&& BaseController.getControllers(DisplayImageFullController.class).size() == 0;

		mPaneButton.setVisible(enabled);
		MenuController.getInstance().setSplitWindowEnabled(enabled);
	}

}
