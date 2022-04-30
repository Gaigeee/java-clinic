package com.clinic.abstracts;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.clinic.Pagination;
import com.clinic.factories.EntityRepositoryFactory;
import com.clinic.interfaces.Copyable;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * A GUI controller to do CRUD operation for an entity.<br>
 * The entity should extend <code>AbstractEntity</code> and implements 
 * <code>Copyable</code> interface. The <code>copy()<code> method will be used
 * when creating entity's form.
 * The entity should have corresponding repository for this controller to do
 * CRUD operation into database
 * 
 * TODO: Child Crud automatic parent field value
 * @author Jose Ryu Leonesta <jose.leonesta@student.matanauniversity.ac.id>
 */
public abstract class AbstractCrudController<T extends AbstractEntity & Copyable<T>, S extends AbstractEntityRepository<T>> {
    public final static int CREATE_ACTION = 1, UPDATE_ACTION = 2, DELETE_ACTION = 3;
    public TableView<T> entityTable;
    public Button createButton;
    public Button updateButton;
    public Button deleteButton;

    private Class<T> entityClass;
    private GridPane formGrid;
    private Scene formScene;
    private Scene mainScene;
    private BooleanProperty selectedItemProperty;
    private T pickResult;
    private String currentFetchWhereClause;

    protected S repo;
    protected List<AbstractCrudController<?, ?>> childControllers;

    protected AbstractCrudController(Class<T> entityClass, Class<S> repoClass) {
        this.entityClass = entityClass;
        this.repo = EntityRepositoryFactory.getRepository(repoClass);
        this.selectedItemProperty = new SimpleBooleanProperty(true);
        this.childControllers = new ArrayList<>();
        this.currentFetchWhereClause = "";
        entityTable = new TableView<>();
        initTableViewSchema();
        entityTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<T>() {
            @Override
            public void changed(ObservableValue<? extends T> o, T oldVal, T newVal) {
                selectedItemProperty.setValue(newVal == null);
            }
        });
        initMainScene();
        initFormGrid();
        formScene = new Scene(formGrid);
    }

    /**
     * Set the form fields in the grid for creating and updating entities.<br>
     * This method is meant to bind form fields to an <code>entity</code>
     * provided by the method.<br>
     * IMPORTANT: form SHOULD contain submit button generated by the method
     * <code>generateSubmitButton()</code>
     * @param formGrid the form grid for form fields to be placed
     * @param entity the entity that should be binded to the form fields
     */
    protected abstract void setFormGrid(GridPane formGrid, T entity);

    /**
     * Set the current fetching where clause for controller to query
     * @param whereClause
     */
    public void setCurrentFetchWhereClause(String whereClause) {
        this.currentFetchWhereClause = whereClause;
    }

    /**
     * Fetch entity data and set it into the table view.
     * @param whereClause the where clause on the query to perform example WHERE foreign_id=1
     */
    public void fetchEntitiesToTable(String whereClause) {
        ObservableList<T> entities;
        Pagination page = new Pagination();
        try {
            entities = FXCollections.observableArrayList(repo.get(page, whereClause));
            entityTable.setItems(entities);
        } catch (SQLException e) {
            System.out.println("Exception caught in AbstractController.fetchEntitiesToTable(): " + e.toString());
        }
    }

    /**
     * Fetch entity data and set it into the table view.
     */
    public void fetchEntitiesToTable() {
        fetchEntitiesToTable(currentFetchWhereClause);
    }

    /**
     * Show a table and a pick button to pick an entity from the table
     * @return the selected entity
     */
    public T pickEntity() {
        VBox pickLayout = new VBox();
        pickLayout.setAlignment(Pos.TOP_LEFT);
        pickLayout.setSpacing(10.0);
        pickLayout.setPadding(new Insets(20));
        Button pickButton = new Button("Pick");
        pickButton.disableProperty().bind(selectedItemProperty);
        pickLayout.getChildren().addAll(
            pickButton,
            entityTable
        );
        Scene pickScene = new Scene(pickLayout);
        Stage pickStage = new Stage();
        pickButton.setOnAction((event) -> {
            T selectedItem = entityTable.getSelectionModel().getSelectedItem();
            pickResult = getNewEntityInstance(selectedItem.getId()).copy(selectedItem);
            pickStage.close();
        });
        pickStage.setTitle("Pick " + entityClass.getSimpleName());
        pickStage.setScene(pickScene);
        pickStage.showAndWait();
        return pickResult;
    }

    /**
     * Get the main CRUD scene of the controller. <br>
     * WARNING: entity data is not fetched with this method, so you should
     * explicitly call <code>fetchEntitiesToTable()</code>
     * @return scene containing table view and button actions
     */
    public Scene getMainScene() {
        return mainScene;
    }

    /**
     * Show a stage to create an entity
     */
    public void showCreateForm() {
        showForm(CREATE_ACTION);
    }

    /**
     * Show a stage to update an entity
     */
    public void showUpdateForm() {
        showForm(UPDATE_ACTION);
    }

    /**
     * Show a confirmation dialog to delete an entity
     */
    public void showDeleteForm() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete data?");
        confirmation.showAndWait();
        if (confirmation.getResult() == ButtonType.OK) {
            actEntity(entityTable.getSelectionModel().getSelectedItem(), DELETE_ACTION);
            fetchEntitiesToTable();
        }
    }

    /**
     * Initializes new grid object and show a stage to do entity creation
     * or update.
     * @param action <code>CREATE_ACTION</code> or <code>UPDATE_ACTION</code>
     */
    private void showForm(int action) {
        initFormGrid();
        T entity = action == CREATE_ACTION
                ? getNewEntityInstance(null)
                : getCopyOfSelectedItem();
        setFormGrid(formGrid, entity);
        if (!childControllers.isEmpty())
            for (AbstractCrudController<?, ?> controller : childControllers) {
                if (entity.getId() != null)
                    controller.setCurrentFetchWhereClause("WHERE " +
                            AbstractEntityRepository
                                    .normalizeFieldName(entityClass.getSimpleName())
                            + "_id=" + entity.getId());
                    controller.fetchEntitiesToTable();
            }
        formScene.setRoot(formGrid);
        Stage formStage = new Stage();
        formStage.setScene(formScene);
        formStage.setTitle(action == CREATE_ACTION ? "Create "
                : "Update " +
                        entityClass.getSimpleName());
        formStage.showAndWait();
    }

    /**
     * Generates a button that handle form submission
     * @param text text to be displayed on the button
     * @param entity the entity that should be created or updated
     * @return <code>Button</code> that has handler that handles form submission
     */
    protected Button generateSubmitButton(String text, T entity) {
        Button submitButton = new Button();
        submitButton.setText(text);
        submitButton.setOnAction((event) -> {
            int action = entity.getId() != 0 ? UPDATE_ACTION : CREATE_ACTION;
            actEntity(entity, action);
            Stage stage = (Stage) submitButton.getScene().getWindow();
            stage.close();
            fetchEntitiesToTable();
        });
        return submitButton;
    }

    /**
     * Do database operation on an entity
     * @param entity entity to be acted upon
     * @param action <code>CREATE_ACTION</code> or <code>UPDATE_ACTION</code> or <code>DELETE_ACTION</code>
     */
    protected void actEntity(T entity, int action) {
        try {
            if (action == CREATE_ACTION)
                repo.create(entity);
            else if (action == UPDATE_ACTION)
                repo.edit(entity);
            else if (action == DELETE_ACTION)
                repo.delete(entity.getId());
            else
                System.out.println("AbstractController.actEntity(): Invalid action");
        } catch (SQLException e) {
            System.out.println("Exception caught in AbstractController.actEntity(): " + e.toString());
        }
    }

    /**
     * Initialize main scene which configures button and adds them along with
     * table view.
     */
    private void initMainScene() {
        createButton = new Button("Create");
        updateButton = new Button("Update");
        deleteButton = new Button("Delete");
        createButton.setOnAction(event -> showCreateForm());
        updateButton.setOnAction(event -> showUpdateForm());
        deleteButton.setOnAction(event -> showDeleteForm());

        updateButton.disableProperty().bind(selectedItemProperty);
        deleteButton.disableProperty().bind(selectedItemProperty);

        HBox buttonLayout = new HBox();
        buttonLayout.setSpacing(5.0);
        buttonLayout.getChildren().addAll(createButton, updateButton, deleteButton);

        VBox sceneLayout = new VBox();
        sceneLayout.setAlignment(Pos.CENTER);
        sceneLayout.setSpacing(10.0);
        sceneLayout.setPadding(new Insets(20));
        sceneLayout.getChildren().addAll(
            new Label(entityClass.getSimpleName()),
            buttonLayout,
            entityTable);
        mainScene = new Scene(sceneLayout);
    }

    /**
     * Initialize the column that the table should display
     */
    protected void initTableViewSchema() {
        T entityInstance = getNewEntityInstance(null);
        TableColumn<T, Integer> idColumn = new TableColumn<>("Id");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        entityTable.getColumns().add(idColumn);
        for (Method method : repo.getEntityAttributeGetters()) {
            if (entityInstance.getTableFieldNames() == null
                    || entityInstance
                            .getTableFieldNames()
                            .contains(repo.normalizeFieldName(method.getName().substring(3)))) {
                TableColumn<T, Serializable> tableColumn = new TableColumn<>(
                        method.getName().substring(3).replaceAll("([a-z])([A-Z])", "$1 $2"));
                tableColumn.setPrefWidth(method.getName().length() * 8);
                tableColumn.setCellValueFactory(new PropertyValueFactory<>(
                        method.getName().substring(3, 4).toLowerCase() +
                                method.getName().substring(4)));
                entityTable.getColumns().add(tableColumn);
            }
        }
    }

    /**
     * Add a column to the current table using property of entity
     * @param columnLabel the label to display in the table heading
     * @param tableColumnKey the column key for the <code>PropertyValueFactory</code>
     * @param prefWidth the prefWidth of the table column
     */    
    protected void addTableColumn(String columnLabel, String tableColumnKey, Double prefWidth) {
        TableColumn<T, Serializable> tableColumn = new TableColumn<>(columnLabel);
        tableColumn.setPrefWidth(prefWidth);
        tableColumn.setCellValueFactory(new PropertyValueFactory<>(tableColumnKey));
        entityTable.getColumns().add(tableColumn);
    }

    /**
     * Add a column to the current table using property of entity
     * @param columnLabel the label to display in the table heading
     * @param tableColumnKey the column key for the <code>PropertyValueFactory</code>
     */    
    protected void addTableColumn(String columnLabel, String tableColumnKey) {
        addTableColumn(columnLabel, tableColumnKey, (double)columnLabel.length() * 8);
    }

    /**
     * Add a column to the current table using callback
     * @param tableColumn the tableColumn object to be added to the table
     * @param callBack the callback to get the value of <code>T</code> to display in the cell
     * @param prefWidth the prefWidth of the table column
     */
    protected <M> void addTableColumn(TableColumn<T, M> tableColumn, Callback<CellDataFeatures<T, M>, ObservableValue<M>> callback, Double prefWidth) {
        tableColumn.setPrefWidth(prefWidth);
        tableColumn.setCellValueFactory(callback);
        entityTable.getColumns().add(tableColumn);
    }

    /**
     * Add a column to the current table using callback
     * @param tableColumn the tableColumn object to be added to the table
     * @param callBack the callback to get the value of <code>T</code> to display in the cell
     */
    protected <M> void addTableColumn(TableColumn<T, M> tableColumn, Callback<CellDataFeatures<T, M>, ObservableValue<M>> callback) {
        addTableColumn(tableColumn, callback, (double)tableColumn.getText().length() * 8);
    }

    /**
     * Get copy of selected item in the table.
     * @return new identical entity object with the selected item.
     */
    private T getCopyOfSelectedItem() {
        T selectedItem = entityTable.getSelectionModel().getSelectedItem();
        try {
            return entityClass
                    .getConstructor(Integer.class)
                    .newInstance(selectedItem.getId())
                    .copy(selectedItem);
        } catch (Exception e) {
            System.out.println("Exception caught in AbstractCrudController.getCopyOfSelectedItem(): " + e.toString());
        }
        return null;
    }

    /**
     * Generates new entity instance
     * @param id id of entity
     */
    private T getNewEntityInstance(Integer id) {
        try {
            return entityClass.getConstructor(Integer.class).newInstance(id);
        } catch (Exception e) {
            System.out.println("Exception caught in AbstractCrudController.getNewEntityInstance(): " + e.toString());
        }
        return null;
    }

    /**
     * Set form grid alignments and spaces.
     */
    private void initFormGrid() {
        formGrid = new GridPane();
        formGrid.setAlignment(Pos.TOP_LEFT);
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.setPadding(new Insets(25));
    }

    /**
     * Get class of the entity for picking entity purpose
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Get repository of the entity
     */
    public S getRepo() {
        return repo;
    }
}
