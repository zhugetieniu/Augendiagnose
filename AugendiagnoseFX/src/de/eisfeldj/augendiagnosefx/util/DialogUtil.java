package de.eisfeldj.augendiagnosefx.util;

import java.io.IOException;
import java.io.Serializable;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import de.eisfeldj.augendiagnosefx.Application;
import de.eisfeldj.augendiagnosefx.controller.DialogController;

/**
 * Helper class to show standard dialogs.
 */
public abstract class DialogUtil {
	/**
	 * Display an error and go back to the current activity.
	 *
	 * @param resource
	 *            the error message resource
	 * @param args
	 *            arguments for the error message
	 */
	public static void displayError(final String resource, final Object... args) {
		String message = String.format(ResourceUtil.getString(resource), args);
		Logger.warning("Dialog message: " + message);

		DialogController controller;
		try {
			controller = (DialogController) FXMLUtil.getRootFromFxml("DialogError.fxml");
		}
		catch (IOException e) {
			Logger.error("Failed to load FXML file for dialog", e);
			return;
		}

		controller.setHeading(ResourceUtil.getString(ResourceConstants.TITLE_DIALOG_ERROR));
		controller.setMessage(message);

		Scene scene = new Scene(controller.getRoot());
		Stage dialog = new Stage();
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(Application.getStage());
		dialog.setScene(scene);

		controller.getBtnBack().setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				dialog.close();
			}
		});

		dialog.show();
	}

	/**
	 * Display a confirmation message asking for cancel or ok.
	 *
	 * @param listener
	 *            The listener waiting for the response
	 * @param buttonResource
	 *            the display on the positive button
	 * @param messageResource
	 *            the confirmation message
	 * @param args
	 *            arguments for the confirmation message
	 */
	public static void displayConfirmationMessage(
			final ConfirmDialogListener listener, final String buttonResource,
			final String messageResource, final Object... args) {

		String message = String.format(ResourceUtil.getString(messageResource), args);

		DialogController controller;
		try {
			controller = (DialogController) FXMLUtil.getRootFromFxml("DialogConfirm.fxml");
		}
		catch (IOException e) {
			Logger.error("Failed to load FXML file for dialog", e);
			return;
		}

		controller.setHeading(ResourceUtil.getString(ResourceConstants.TITLE_DIALOG_CONFIRMATION));
		controller.setMessage(message);
		if (buttonResource != null) {
			String buttonText = String.format(ResourceUtil.getString(buttonResource));
			controller.getBtnOk().setText(buttonText);
		}

		Scene scene = new Scene(controller.getRoot());
		Stage dialog = new Stage();
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(Application.getStage());
		dialog.setScene(scene);

		controller.getBtnCancel().setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				dialog.close();
				listener.onDialogNegativeClick();
			}
		});
		controller.getBtnOk().setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				dialog.close();
				listener.onDialogPositiveClick();
			}
		});

		dialog.show();
	}

	/**
	 * Display a progress dialog.
	 *
	 * @param messageResource
	 *            the displayed message
	 * @param args
	 *            arguments for the displayed message
	 * @return A reference to the dialog.
	 */
	public static ProgressDialog displayProgressDialog(final String messageResource, final Object... args) {
		String message = String.format(ResourceUtil.getString(messageResource), args);

		DialogController controller;
		try {
			controller = (DialogController) FXMLUtil.getRootFromFxml("DialogProgress.fxml");
		}
		catch (IOException e) {
			Logger.error("Failed to load FXML file for dialog", e);
			return null;
		}

		controller.setHeading(ResourceUtil.getString(ResourceConstants.TITLE_DIALOG_PROGRESS));
		controller.setMessage(message);

		Scene scene = new Scene(controller.getRoot());
		Stage dialog = new Stage();
		dialog.initStyle(StageStyle.UNDECORATED);
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(Application.getStage());
		dialog.setScene(scene);
		dialog.show();

		return new ProgressDialog(dialog, controller);
	}

	/**
	 * A progress dialog.
	 */
	public static final class ProgressDialog {
		/**
		 * The stage.
		 */
		private Stage stage;

		/**
		 * The controller.
		 */
		private DialogController controller;

		/**
		 * Constructor setting the stage and the controller.
		 *
		 * @param stage
		 *            The stage.
		 * @param controller
		 *            The controller.
		 */
		private ProgressDialog(final Stage stage, final DialogController controller) {
			this.stage = stage;
			this.controller = controller;
		}

		/**
		 * Getter for the stage.
		 *
		 * @return The stage.
		 */
		public Stage getStage() {
			return stage;
		}

		/**
		 * Getter for the scene.
		 *
		 * @return The scene.
		 */
		public Scene getScene() {
			return stage.getScene();
		}

		/**
		 * Getter for the controller.
		 *
		 * @return The controller.
		 */
		public DialogController getController() {
			return controller;
		}

		/**
		 * Close the dialog.
		 */
		public void close() {
			stage.close();
		}
	}

	/**
	 * The activity that creates an instance of this dialog listFoldersFragment must implement this interface in order
	 * to receive event callbacks. Each method passes the DialogFragment in case the host needs to query it.
	 */
	public interface ConfirmDialogListener extends Serializable {
		/**
		 * Callback method for positive click from the confirmation dialog.
		 */
		void onDialogPositiveClick();

		/**
		 * Callback method for negative click from the confirmation dialog.
		 */
		void onDialogNegativeClick();
	}

}
