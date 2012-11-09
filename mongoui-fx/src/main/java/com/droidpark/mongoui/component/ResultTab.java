package com.droidpark.mongoui.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bson.types.ObjectId;

import com.droidpark.mongoui.util.ImageUtil;
import com.droidpark.mongoui.util.Language;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.util.JSON;

import static com.droidpark.mongoui.util.LanguageConstants.*;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Callback;

public class ResultTab extends Tab implements UITab {

	private static Logger logger = Logger.getLogger(ResultTab.class);
	private Integer dataLimitValue = 100;
	private Integer dataSkipValue = 0;
	
	private Mongo mongo;
	private String collectionName;
	private String databaseName;
	
	List<String> columns;
	TableView<DBObject> tableView = new TableView<DBObject>();
	ObservableList<DBObject> dataList;
	
	
	AnchorPane tabToolPane;
	AnchorPane tabFooterPane;
	SplitPane horizontalPane;
	AnchorPane tableAncPane;
	AnchorPane columnAncWrapPane;
	AnchorPane columnAncPane;
	TreeView<CheckBox> columnTreePane;
	AnchorPane columnTreeWrapPane;
	TitledPane columnTitledPane;
	BorderPane footerBorder;
	
	ModalDialog filterDialog = null;
	
	Label resultSizeLabel = new Label();
	Label navInfoLabel = new Label();
	
	int resultSize = 0;
	private Gson gson = null;
	
	public ResultTab(String collection, String database, Mongo mongo) {
		super(database + "." + collection);
		this.collectionName = collection;
		this.databaseName = database;
		this.mongo = mongo;
		initComponent();
	}
	
	private void initComponent() {
		initLayout();
		initDataListAndColumnList(null);
		initResultTable();
		initColumnViewPane();
		initFooterPane();
		initToolPane();
		initFilterDialog();
		initLabels();
		
		BorderPane borderPane = new BorderPane();
		borderPane.setTop(tabToolPane);
		borderPane.setCenter(horizontalPane);
		borderPane.setBottom(tabFooterPane);
		setContent(borderPane);
	}
	
	private void initLabels() {
		navInfoLabel.setStyle("-fx-padding: 2px 5px;");
	}
	
	private void initDataListAndColumnList(BasicDBObject query) {
		try {
			dataList = FXCollections.observableArrayList();
			columns = new ArrayList<String>();
			query = query == null ? new BasicDBObject() : query;
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gson = gsonBuilder.serializeNulls().create();
			DB database = mongo.getDB(databaseName);
			DBCollection collection = database.getCollection(collectionName);
			
			resultSize = collection.find(query).skip(dataSkipValue).size();
			resultSizeLabel.setText(resultSize + " Row(s)");
			int navInfoSize = resultSize < dataLimitValue ? resultSize : dataLimitValue;
			navInfoLabel.setText(dataSkipValue + " - " + navInfoSize + " of " + resultSize);
			logger.info("db." + collectionName + ".find("+query.toString()+").skip("+dataSkipValue+").size()");
			
			DBCursor cursor = collection.find(query).skip(dataSkipValue).limit(dataLimitValue);
			logger.info("db." + collectionName + ".find("+query.toString()+").skip("+dataSkipValue+").limit("+dataLimitValue+")");
			
			Set<String> columnsSet = new HashSet<String>();
			while(cursor.hasNext()) {
				DBObject object = cursor.next();
				object.isPartialObject();
				dataList.add(object);
				for(String column : object.keySet()) {
					columnsSet.add(column);
				}
				
			}
			columns.addAll(columnsSet);
			Collections.sort(columns);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void initResultTable() {
		tableAncPane = new AnchorPane();
		tableAncPane.getChildren().add(tableView);
		
		tableView.setMinSize(0, 0);
		tableView.prefHeightProperty().bind(tableAncPane.heightProperty());
		tableView.prefWidthProperty().bind(tableAncPane.widthProperty());
		
		horizontalPane.getItems().add(tableAncPane);
		refreshTableView();
		
	}
	
	private void refreshTableView() {
		clearTableViewData();
		for(final String columnName : columns) {
			TableColumn<DBObject, String> column = new TableColumn<DBObject, String>();
			column.setText(columnName);
			column.setMinWidth(100);
			
			column.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<DBObject,String>, ObservableValue<String>>() {
				public ObservableValue<String> call(CellDataFeatures<DBObject, String> cell) {
					if(cell.getValue() != null) {
						DBObject obj = cell.getValue();
						JsonElement entity = gson.toJsonTree(obj.get(columnName));
						String value = "";
						if(obj.get(columnName) instanceof ObjectId) {
							value = obj.get(columnName).toString();
						}
						else if(entity.isJsonPrimitive()) {
							value = entity.getAsString();
						}
						else if(entity.isJsonObject()) {
							value = "{" + entity.getAsJsonObject().entrySet().size() + " fields}";
						}
						else if(entity.isJsonArray()) {
							value = "{" + entity.getAsJsonArray().size() + " fields}";
						}
						return new SimpleStringProperty(value);
					}
					return null;
				}
			});
			
			tableView.getColumns().add(column);
			tableView.getSelectionModel().setCellSelectionEnabled(true);
			tableView.setItems(dataList);
		}
	}
	
	private void clearTableViewData() {
		tableView.getColumns().clear();
		tableView.getItems().clear();
	}
	
	private void initLayout() {
		tabToolPane = new AnchorPane();
		tabToolPane.getStyleClass().add("-mongoui-tab-toolpane");
		tabToolPane.setPrefHeight(30);
		
		tabFooterPane = new AnchorPane();
		tabFooterPane.getStyleClass().add("-mongoui-tab-footerpane");
		tabFooterPane.setPrefHeight(30);
		
		horizontalPane = new SplitPane();
		horizontalPane.setOrientation(Orientation.HORIZONTAL);
		horizontalPane.setDividerPosition(0,0.8);
	}
	
	private void initColumnViewPane() {
		TreeItem<CheckBox> rootField = new TreeItem<CheckBox>(new CheckBox(collectionName));
		rootField.setExpanded(true);
		rootField.getValue().setDisable(true);
		rootField.getValue().setSelected(true);
		final TableView<DBObject> table = tableView;
		for(final String column : columns) {
			final TreeItem<CheckBox> check = new TreeItem<CheckBox>();
			check.setValue(new CheckBox());
			check.getValue().setText(column);
			check.getValue().setSelected(true);
			check.getValue().setOnAction(new EventHandler<ActionEvent>() {
				public void handle(ActionEvent arg0) {
					List<String> tempcolumn = new ArrayList<String>();
					for(TableColumn column : table.getColumns()) {
						tempcolumn.add(column.getText());
					}
					int index = tempcolumn.indexOf(column);
					tempcolumn.clear();
					table.getColumns().get(index).setVisible(check.getValue().isSelected());
				}
			});
			rootField.getChildren().add(check);
		}
		
		columnAncWrapPane = new AnchorPane();
		columnAncPane = new AnchorPane();
		columnAncPane.setMinSize(0, 0);
		columnAncPane.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
		columnAncPane.prefHeightProperty().bind(columnAncWrapPane.heightProperty());
		columnAncPane.prefWidthProperty().bind(columnAncWrapPane.widthProperty());
		columnAncWrapPane.getChildren().add(columnAncPane);
		
		columnTreePane = new TreeView<CheckBox>(rootField);
		columnTreeWrapPane = new AnchorPane();
		columnTreeWrapPane.setPrefWidth(150);
		columnTreeWrapPane.setMinSize(0, 0);
		columnTreeWrapPane.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
		columnTreeWrapPane.getChildren().add(columnTreePane);
		
		columnTreePane.prefHeightProperty().bind(columnTreeWrapPane.heightProperty());
		columnTreePane.prefWidthProperty().bind(columnTreeWrapPane.widthProperty());
		
		columnTitledPane = new TitledPane("Collection Fields", columnTreeWrapPane);
		columnTitledPane.prefHeightProperty().bind(columnAncPane.heightProperty());
		columnTitledPane.prefWidthProperty().bind(columnAncPane.widthProperty());
		columnTitledPane.setCollapsible(false);
		columnAncPane.getChildren().add(columnTitledPane);
		horizontalPane.getItems().add(columnAncWrapPane);
	}
	
	private void initFooterPane() {
		footerBorder = new BorderPane();
		footerBorder.prefWidthProperty().bind(tabFooterPane.widthProperty());
		footerBorder.prefHeightProperty().bind(tabFooterPane.heightProperty());
		tabFooterPane.getChildren().add(footerBorder);
		
		HBox resultInfoBox = new HBox();
		footerBorder.setLeft(resultInfoBox);
		resultInfoBox.getChildren().add(new Label("Result: "));
		resultInfoBox.getChildren().add(resultSizeLabel);
		resultInfoBox.setStyle("-fx-padding: 4px;");
		
		HBox resultNavBox = new HBox();
		resultNavBox.setStyle("-fx-padding: 4px;");
		footerBorder.setRight(resultNavBox);
		Button prev = new Button("", new ImageView(ImageUtil.PREV_16_16));
		prev.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		resultNavBox.getChildren().add(prev);
				
		
		resultNavBox.getChildren().add(navInfoLabel);
		
		Button next = new Button("", new ImageView(ImageUtil.NEXT_16_16));
		next.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		resultNavBox.getChildren().add(next);
	}
	
	private void initToolPane() {
		HBox toolBox = new HBox();
		toolBox.setStyle("-fx-padding: 4px;");
		tabToolPane.getChildren().add(toolBox);
		initToolButtons(toolBox);
	}
	
	private void initToolButtons(HBox toolBox) {
		Button refresh = new Button("Refresh", new ImageView(ImageUtil.TB_DB_REFRESH_16_16));
		refresh.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toolBox.getChildren().add(refresh);
		
		Button add = new Button("Add", new ImageView(ImageUtil.TB_DB_ADD_16_16));
		add.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toolBox.getChildren().add(add);
		
		Button remove = new Button("Remove", new ImageView(ImageUtil.TB_DB_REMOVE_16_16));
		remove.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toolBox.getChildren().add(remove);
		
		Button filter = new Button("Filter", new ImageView(ImageUtil.TB_DB_FILTER_16_16));
		filter.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toolBox.getChildren().add(filter);
		filter.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent arg0) {
				filterDialog.showModalDialog();
			}
		});
		
		
		Button document = new Button("Document", new ImageView(ImageUtil.TB_DB_DOC_16_16));
		document.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toolBox.getChildren().add(document);
		document.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent arg0) {
				ModalDialog dialog = ModalDialog.createMessageDialog("Warn Dialog", "Example Waringing Dialog!", ModalDialog.WARN);
				dialog.showModalDialog();
			}
		});
	}
	
	private void initFilterDialog() {
		filterDialog = new ModalDialog(Language.get(DIALOG_TITLE_FILTER), 400, 150, ImageUtil.MD_DB_FILTER_24_24);
		final ModalDialog dialog = filterDialog;
		
		final GridPane grid = new GridPane();
		grid.setStyle("-fx-padding: 10px;");
		//query
		Label queryLabel = new Label(Language.get(LABEL_QUERY) + ": ");
		queryLabel.setStyle("-fx-padding: 0px 10px 0px 0px;");
		grid.add(queryLabel, 0,0);
		final TextField queryText = new TextField();
		grid.add(queryText, 1,0,5,1);
		
		
		//Sort
		Label sortLabel = new Label(Language.get(LABEL_SORT) + ": ");
		sortLabel.setStyle("-fx-padding: 0px 10px 0px 0px;");
		grid.add(sortLabel,0,1);
		final TextField sortText = new TextField();
		sortText.setPrefWidth(100);
		grid.add(sortText, 1,1);
		
		//Skip
		Label skipLabel = new Label(Language.get(LABEL_SKIP) + ": ");
		skipLabel.setStyle("-fx-padding: 0px 10px 0px 20px;");
		grid.add(skipLabel, 2, 1);
		final TextField skipText = new TextField(dataSkipValue.toString());
		skipText.setPrefWidth(50);
		grid.add(skipText, 3, 1);
		
		//Limit
		Label limitLabel = new Label(Language.get(LABEL_LIMIT) + ": ");
		limitLabel.setStyle("-fx-padding: 0px 10px 0px 20px;");
		grid.add(limitLabel, 4, 1);
		final TextField limitText = new TextField(dataLimitValue.toString());
		limitText.setPrefWidth(50);
		grid.add(limitText, 5, 1);
		
		dialog.setContent(grid);
		
		Button cancelButton = new Button(Language.get(BUTTON_CANCEL));
		dialog.addNodeToFooter(cancelButton);
		cancelButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent arg0) {
				dialog.hideModalDialog();
			}
		});
		
		Button filterButton = new Button(Language.get(BUTTON_FILTER));
		dialog.addNodeToFooter(filterButton);
		filterButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent arg0) {
				try {
					BasicDBObject query = (BasicDBObject) JSON.parse(queryText.getText());
					dataLimitValue = Integer.valueOf(limitText.getText());
					dataSkipValue = Integer.valueOf(skipText.getText());
					initDataListAndColumnList(query);
					refreshTableView();
					dialog.hideModalDialog();
				}
				catch (Exception e) {
					logger.error(e.getMessage(), e);
				}
			}
		});
		
	}
	
	private String getFilterChar(String val) {
		if(val == ">") return "$gt";
		else if(val == "<") return "$lt";
		else if(val == ">=") return "$gte";
		else if(val == "<=") return "$lte";
		else if(val == "exists") return "exists";
		else return null;
	}
	
	
	public void destroy() {
		tableView.prefHeightProperty().unbind();
		tableView.prefWidthProperty().unbind();
		columnAncPane.prefHeightProperty().unbind();
		columnAncPane.prefWidthProperty().unbind();
		columnTreePane.prefHeightProperty().unbind();
		columnTreePane.prefWidthProperty().unbind();
		columnTitledPane.prefHeightProperty().unbind();
		columnTitledPane.prefWidthProperty().unbind();
		footerBorder.prefWidthProperty().unbind();
		footerBorder.prefHeightProperty().unbind();
		tableView.getColumns().clear();
		tableView.setItems(null);
		tableView = null;
		dataList.clear();
		dataList = null;
		columnTreePane.getRoot().getChildren().clear();
		columnTreePane.setRoot(new TreeItem<CheckBox>());
		filterDialog.destroy();
	}
	
}
