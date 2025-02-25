package ru.shemplo.tbs.gfx;

import static ru.shemplo.tbs.TBSConstants.*;
import static ru.shemplo.tbs.gfx.TBSStyles.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;

import com.panemu.tiwulfx.control.NumberField;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import ru.shemplo.tbs.MappingROProperty;
import ru.shemplo.tbs.TBSBackgroundExecutor;
import ru.shemplo.tbs.TBSBalanceController;
import ru.shemplo.tbs.TBSBondManager;
import ru.shemplo.tbs.TBSClient;
import ru.shemplo.tbs.TBSConstants;
import ru.shemplo.tbs.TBSExcelUtils;
import ru.shemplo.tbs.TBSLogWrapper;
import ru.shemplo.tbs.TBSPlanner;
import ru.shemplo.tbs.TBSPlanner.DistributionCategory;
import ru.shemplo.tbs.TBSUtils;
import ru.shemplo.tbs.entity.IPlanningBond;
import ru.shemplo.tbs.entity.IProfile;
import ru.shemplo.tbs.entity.LinkedObject;
import ru.shemplo.tbs.entity.LinkedSymbolOrImage;
import ru.shemplo.tbs.gfx.component.SliderWithField;
import ru.shemplo.tbs.gfx.component.TileWithHeader;
import ru.shemplo.tbs.gfx.table.TBSEditTableCell;
import ru.tinkoff.invest.openapi.model.rest.BrokerAccountType;
import ru.tinkoff.invest.openapi.model.rest.CandleResolution;
import ru.tinkoff.invest.openapi.model.rest.Currency;
import ru.tinkoff.invest.openapi.model.rest.CurrencyPosition;

@Slf4j
public class TBSPlannerTool extends HBox {
    
    private AreaChart <Number, Number> distributionChart;
    private ChoiceBox <DistributionCategory> typeSelect;
    private DoubleProperty diversificationProperty;
    private NumberField <Double> amountField;
    private TableView <IPlanningBond> table;
    
    public TBSPlannerTool () {
        setPadding (new Insets (2, 0, 0, 0));
        setFillHeight (true);
        
        getChildren ().add (makeLeftPannel ());
        
        getChildren ().add (table = makeTable ());
        HBox.setHgrow (table, Priority.ALWAYS);
        
        TBSPlanner.getInstance ().getBonds ().addListener ((ListChangeListener <IPlanningBond>) (change -> {
            TBSPlanner.getInstance ().updateDistribution ();
            updateChart ();
        }));
        
        updateChart ();
    }
    
    private Parent makeLeftPannel () {
        final var scroll = new ScrollPane ();
        scroll.setPadding (new Insets (10, 16, 12, 16));
        scroll.setBackground (Background.EMPTY);
        scroll.setMinWidth (352);
        scroll.setBorder (null);
        
        final var column = new VBox (4);
        column.setFillWidth (false);
        scroll.setContent (column);
        
        // Input parameters
        
        final var line1 = new HBox (4);
        column.getChildren ().add (line1);
        
        final var typeHeader = new Text ("To distribute");
        typeHeader.setWrappingWidth (100.0);
        line1.getChildren ().add (typeHeader);
        
        final var amounHeader = new Text ("Amount");
        amounHeader.setWrappingWidth (200.0);
        line1.getChildren ().add (amounHeader);
        
        final var line2 = new HBox (4);
        VBox.setMargin (line2, new Insets (0, 0, 12, 0));
        column.getChildren ().add (line2);
        
        typeSelect = new ChoiceBox <DistributionCategory> ();
        typeSelect.getItems ().setAll (DistributionCategory.values ());
        typeSelect.setMinWidth (typeHeader.getWrappingWidth ());  
        //typeSelect.setValue (DistributionCategory.SUM);
        typeSelect.setValue (TBSPlanner.getInstance ().getCategory ());
        line2.getChildren ().add (typeSelect);
        
        amountField = new NumberField <> (Double.class);
        amountField.setMinWidth (amounHeader.getWrappingWidth () - 40.0);
        amountField.setValue (TBSPlanner.getInstance ().getAmount ());
        line2.getChildren ().add (amountField);
        
        final var syncBalanceIcon = new ImageView (TBSApplicationIcons.sync24);
        syncBalanceIcon.setFitHeight (20);
        syncBalanceIcon.setFitWidth (20);
        
        final var syncBalanceButton = new Button (null, syncBalanceIcon);
        syncBalanceButton.setTooltip (new Tooltip ("Load total RUB balance from your Tinkoff accounts"));
        syncBalanceButton.setPadding (new Insets (2, 0, 2, 8));
        syncBalanceButton.setBackground (Background.EMPTY);
        syncBalanceButton.setCursor (Cursor.HAND);
        syncBalanceButton.disableProperty ().bind (Bindings.notEqual (
            typeSelect.valueProperty (), DistributionCategory.SUM
        ));
        syncBalanceButton.setOnMouseClicked (me -> {
            if (TBSUIUtils.SIMPLE_CLICK.test (me)) {
                syncBalance (TBSUIApplication.getInstance ().getProfile ());
            }
        });
        line2.getChildren ().add (syncBalanceButton);
        
        // Diversification parameter
        
        final var diversificationSlider = new SliderWithField <> (Double.class, 0.0, 100.0, 100.0);
        diversificationSlider.setMinWidth (300.0);
        
        final var diversificationTile = new TileWithHeader <> ("Diversification, %", diversificationSlider);
        column.getChildren ().add (diversificationTile);
        
        // Chart
        
        final var xAxis = new NumberAxis ();
        //xAxis.setMinorTickVisible (true);
        
        distributionChart = new AreaChart <> (xAxis, new NumberAxis ());
        distributionChart.setMinWidth (
            typeSelect.getMinWidth () + amountField.getMinWidth () + syncBalanceIcon.getFitWidth () 
            + line2.getSpacing () * 2
        );
        distributionChart.setMaxWidth  (distributionChart.getMinWidth ());
        distributionChart.setMaxHeight (distributionChart.getMaxWidth () * 1.5);
        VBox.setMargin (distributionChart, new Insets (0, 0, 24, 0));
        distributionChart.setCreateSymbols (false);
        distributionChart.setAnimated (false);
        column.getChildren ().add (distributionChart);
        
        // Price recommendation
        
        final var lineRecommend = new HBox (12);
        lineRecommend.setAlignment (Pos.CENTER_LEFT);
        column.getChildren ().add (lineRecommend);
        
        final var recommendIcon = new ImageView (TBSApplicationIcons.range);
        recommendIcon.setFitHeight (20);
        recommendIcon.setFitWidth (20);
        
        final var defaultDays = TBSPlanner.getInstance ().getAnalyzeDays ();
        final var daysSlider = new SliderWithField <> (Double.class, 1.0, 14.0, defaultDays);
        daysSlider.getSlider ().setShowTickLabels (true);
        daysSlider.getSlider ().setShowTickMarks (true);
        daysSlider.getSlider ().setMajorTickUnit (2.0);
        daysSlider.getSlider ().setMinorTickCount (1);
        daysSlider.getSlider ().setSnapToTicks (true);
        daysSlider.setMinWidth (300.0 - recommendIcon.getFitWidth () - lineRecommend.getSpacing ());
        
        final var daysTile = new TileWithHeader <> ("Days to analyze for price recommendation", daysSlider);
        lineRecommend.getChildren ().add (daysTile);
        
        final var recommendButton = new Button (null, recommendIcon);
        recommendButton.setTooltip (new Tooltip ("..."));
        recommendButton.setPadding (new Insets (6, 0, 0, 0));
        recommendButton.setBackground (Background.EMPTY);
        recommendButton.setCursor (Cursor.HAND);
        recommendButton.setOnMouseClicked (me -> {
            if (TBSUIUtils.SIMPLE_CLICK.test (me)) {
                final var profile = TBSUIApplication.getInstance ().getProfile ();
                final var days = daysSlider.getValueProperty ().longValue ();
                recommendPrice (profile, days);
            }
        });
        lineRecommend.getChildren ().add (recommendButton);
        
        // Summary rows
        
        final var line5 = new HBox (4);
        column.getChildren ().add (line5);
        
        final var priceHeader = new Text ("Total price");
        priceHeader.setWrappingWidth (200.0);
        line5.getChildren ().add (priceHeader);
        
        final var lotsHeader = new Text ("Total lots");
        amounHeader.setWrappingWidth (100.0);
        line5.getChildren ().add (lotsHeader);
        
        final var line6 = new HBox (4);
        VBox.setMargin (line6, new Insets (0, 0, 12, 0));
        column.getChildren ().add (line6);
        
        final var priceField = new NumberField <> ();
        priceField.valueProperty ().bindBidirectional (TBSPlanner.getInstance ().getSummaryPrice ());
        priceField.setMinWidth (200.0); priceField.setMaxWidth (priceField.getMinWidth ());
        priceField.setEditable (false);
        //priceField.setDisable (true);
        line6.getChildren ().add (priceField);
        
        final var lotsField = new NumberField <> ();
        lotsField.valueProperty ().bindBidirectional (TBSPlanner.getInstance ().getSummaryLots ());
        lotsField.setMinWidth (100.0); lotsField.setMaxWidth (lotsField.getMinWidth ());
        lotsField.setEditable (false);
        //priceField.setDisable (true);
        line6.getChildren ().add (lotsField);
        
        // Export button
        
        final var line7 = new HBox (4);
        column.getChildren ().add (line7);
        
        final var excelExportIcon = new ImageView (TBSApplicationIcons.excel);
        excelExportIcon.setFitHeight (16);
        excelExportIcon.setFitWidth (16);
        
        final var excelExportButton = new Button ("Export", excelExportIcon);
        excelExportButton.setPadding (new Insets (4, 8, 4, 8));
        excelExportButton.setGraphicTextGap (8);
        excelExportButton.setOnMouseClicked (me -> {
            if (TBSUIUtils.SIMPLE_CLICK.test (me)) {
                exportToExcel (TBSPlanner.getInstance ().getBonds ());
            }
        });
        line7.getChildren ().add (excelExportButton);
        
        // Other initializations
        
        final var realSeries = new Series <Number, Number> ();
        realSeries.setName ("Real distr.");
        distributionChart.getData ().add (realSeries);
        
        final var calculatedSeries = new Series <Number, Number> ();
        calculatedSeries.setName ("Calculated distr.");
        distributionChart.getData ().add (calculatedSeries);
        
        typeSelect.valueProperty ().addListener ((__, ___, value) -> {
            TBSPlanner.getInstance ().updateDistributionParameters (
                value, TBSUtils.aOrB (amountField.getValue (), 0.0), 
                diversificationProperty.get ()
            );
            updateChart ();
        });
        
        amountField.valueProperty ().addListener ((__, ___, value) -> {
            TBSPlanner.getInstance ().updateDistributionParameters (
                typeSelect.getValue (), TBSUtils.aOrB (value, 0.0), 
                diversificationProperty.get ()
            );
            updateChart ();
        });
        
        diversificationProperty = diversificationSlider.getValueProperty ();
        diversificationProperty.set (TBSPlanner.getInstance ().getDiversification ());
        diversificationProperty.addListener ((__, ___, div) -> {
            TBSPlanner.getInstance ().updateDistributionParameters (
                typeSelect.getValue (), TBSUtils.aOrB (amountField.getValue (), 0.0), 
                diversificationProperty.get ()
            );
            updateChart ();
        });
        
        return scroll;
    }
    
    private TableView <IPlanningBond> makeTable () {
        final var table = new TableView <IPlanningBond> ();
        table.setBackground (new Background (new BackgroundFill (Color.LIGHTGRAY, CornerRadii.EMPTY, Insets.EMPTY)));
        //HBox.setMargin (table, new Insets (0, 2, 2, 0));
        table.getStylesheets ().setAll (STYLE_TABLES);
        table.setSelectionModel (null);
        table.setBorder (Border.EMPTY);
        
        final var grThreshold = TBSStyles.<IPlanningBond, Number> threshold (0.0, 1e-6);
        final var sameMonth = TBSStyles.<IPlanningBond> sameMonth (NOW);
        final var linkIcon = TBSStyles.<IPlanningBond> linkIcon ();
        
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Integer> buildTBSTableColumn ()
            .name ("#").tooltip (null)
            .alignment (Pos.BASELINE_CENTER).minWidth (30.0).sortable (false)
            .propertyFetcher (bond -> bond.getProperty (IPlanningBond.INDEX_PROPERTY, () -> 0, false))
            .converter (null).highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, String> buildTBSTableColumn ()
            .name ("Name").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (250.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondName
            )).converter ((r, v) -> v)
            .highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, String> buildTBSTableColumn ()
            .name ("Ticker").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (125.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("code", () -> "")).converter ((r, v) -> v)
            .highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Currency> buildTBSTableColumn ()
            .name ("Currency").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondCurrency
            ))
            .highlighter (null).converter ((r, v) -> String.valueOf (v))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Number> buildTBSTableColumn ()
            .name ("👝").tooltip ("Number of lots in your portfolio (sum by all your accounts)")
            .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondLots
            ))
            .highlighter (grThreshold).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Number> buildTBSTableColumn ()
            .name ("Score").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondScore
            ))
            .highlighter (grThreshold).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Number> buildTBSTableColumn ()
            .name ("Price").tooltip ("Last committed price in MOEX")
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondPrice
            ))
            .highlighter (null).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Number> buildTBSTableColumn ()
            .name ("Top price").tooltip ("Recommended purchase price")
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("recommendedPrice", () -> 0.0))
            .highlighter (null).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, LocalDate> buildTBSTableColumn ()
            .name ("Next C R").tooltip ("Closest date to the next coupon record")
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondNextRecord
            ))
            .highlighter (sameMonth).converter ((c, v) -> String.valueOf (v))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, LocalDate> buildTBSTableColumn ()
            .name ("Next C").tooltip ("Closest date to the next coupon")
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.getRWProperty ("code", () -> ""), 
                TBSBondManager::getBondNextCoupon
            ))
            .highlighter (sameMonth).converter ((c, v) -> String.valueOf (v))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Integer> buildTBSTableColumn ()
            .name ("📊").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("amount", () -> 0))
            .converter (null).highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, Integer, NumberField <Integer>> buildTBSEditTableColumn ()
            .name ("").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (100.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.<Integer> getRWProperty ("customValue", () -> null), 
                v -> new LinkedObject <Integer> (bond.getCode (), v)
            ))
            .fieldSupplier (this::makeCustomLotsValueField)
            .converter (null).highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IPlanningBond, LinkedSymbolOrImage> buildTBSIconTableColumn ()
            .name ("🗑").tooltip (null).minWidth (30.0).sortable (false)
            .propertyFetcher (b -> new SimpleObjectProperty <> (LinkedSymbolOrImage.symbol ("⌫", b.getCode ())))
            .onClick ((me, cell) -> TBSPlanner.getInstance ().removeBond (cell.getItem ().getLink ()))
            .highlighter (linkIcon)
            .build ());
        
        return table;
    }
    
    private NumberField <Integer> makeCustomLotsValueField (TBSEditTableCell <IPlanningBond, Integer, NumberField <Integer>> cell) {
        final var field = new NumberField <> (Integer.class);
        field.setPadding (new Insets (1, 4, 1, 4));
        field.valueProperty ().addListener ((__, ___, value) -> {
            TBSUtils.doIfNN (cell.getItem (), item -> {
                final var planner = TBSPlanner.getInstance ();
                final var bond = planner.getBondByTicker (item.getLink ());
                
                TBSUtils.doIfNN (bond, b -> {
                    b.getRWProperty ("customValue", () -> 0).set (value);
                    planner.updateDistribution ();
                    planner.dump ();
                    
                    TBSBalanceController.getInstance ().updateBalance ();
                    updateChart ();
                });
            });
        });
        
        return field;
    }
    
    public void applyData (IProfile profile) {
        final var bonds = TBSPlanner.getInstance ().getBonds ();
        if (table.getItems () != bonds) {            
            table.setItems (bonds);
        }
    }
    
    private void updateChart () {
        final var bonds = TBSPlanner.getInstance ().getBonds ();
        if (bonds.isEmpty ()) { return; }
        
        final var calculatedSeries = distributionChart.getData ().get (1);
        calculatedSeries.getData ().clear ();
        
        final var realSeries = distributionChart.getData ().get (0);
        realSeries.getData ().clear ();
        
        for (int i = 0; i < bonds.size (); i++) {
            final var bond = bonds.get (i);
            final var calculated = bond.getCalculatedAmount ();
            calculatedSeries.getData ().add (new Data <> (i, calculated));
            calculatedSeries.getData ().add (new Data <> (i + 1, calculated));
            realSeries.getData ().add (new Data <> (i, bond.getCurrentValue ()));
            realSeries.getData ().add (new Data <> (i + 1, bond.getCurrentValue ()));
        }
        calculatedSeries.getData ().add (new Data <> (bonds.size (), 0.0));
        realSeries.getData ().add (new Data <> (bonds.size (), 0.0));
    }
    
    private void exportToExcel (List <IPlanningBond> bonds) {
        final var fileChooser = new FileChooser ();
        fileChooser.getExtensionFilters ().add (new ExtensionFilter ("Excel file", List.of ("*.xlsx")));
        fileChooser.setInitialDirectory (new File (System.getProperty ("user.home")));
        fileChooser.setTitle ("Export plan to excel");
        
        final var file = fileChooser.showSaveDialog (TBSUIApplication.getInstance ().getStage ());
        if (file == null) { return; }
        
        TBSBackgroundExecutor.getInstance ().runInBackground (() -> {            
            try (final var wb = new XSSFWorkbook (XSSFWorkbookType.XLSX)) {
                final var sheet = wb.createSheet ("Bonds plan");
                
                int tc = 0;
                sheet.setColumnWidth (tc++, 256 * 4);
                sheet.setColumnWidth (tc++, 256 * 48);
                sheet.setColumnWidth (tc++, 256 * 16);
                sheet.setColumnWidth (tc++, 256 * 20);
                sheet.setColumnWidth (tc++, 256 * 20);
                sheet.setColumnWidth (tc++, 256 * 8);
                sheet.setColumnWidth (tc++, 256 * 16);
                sheet.setColumnWidth (tc++, 256 * 8);
                
                tc = 0;
                TBSExcelUtils.setValue (sheet, 0, tc++, "#");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Bond");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Ticker");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Next coupon (buy date)");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Recommended price");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Amount");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Total price");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Bought?");
                
                tc = 0;
                for (int i = 0; i < bonds.size (); i++, tc = 0) {
                    final var bond = bonds.get (i);
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, i + 1);
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, TBSBondManager.getBondName (bond.getCode ()));
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, bond.getCode ());
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, TBSBondManager.getBondNextCoupon (bond.getCode ()));
                    double price = TBSBondManager.getBondPrice (bond.getCode ()), amount = bond.getCurrentValue ();
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, price);
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, amount);
                    TBSExcelUtils.setValue (sheet, i + 1, tc++, amount * price);
                }
                
                tc = 10;
                sheet.setColumnWidth (tc++, 256 * 12);
                sheet.setColumnWidth (tc++, 256 * 12);
                
                tc = 10;
                TBSExcelUtils.setValue (sheet, 0, tc++, "Total price");
                TBSExcelUtils.setValue (sheet, 0, tc++, "Total lots");
                
                tc = 10;
                TBSExcelUtils.setValue (sheet, 1, tc++, TBSPlanner.getInstance ().getSummaryPrice ().get ());
                TBSExcelUtils.setValue (sheet, 1, tc++, TBSPlanner.getInstance ().getSummaryLots ().getValue ());
                
                wb.write (new FileOutputStream (file));
            } catch (IOException ioe) {
                
            }
        });
    }
    
    private void syncBalance (IProfile profile) {
        TBSBackgroundExecutor.getInstance ().runInBackground (() -> {
            try {
                final var client = TBSClient.getInstance ().getConnection (profile, new TBSLogWrapper ());
                final var accounts = client.getUserContext ().getAccounts ().join ();
                
                double sumRUB = 0;
                for (final var account : accounts.getAccounts ()) {
                    if (account.getBrokerAccountType () != BrokerAccountType.TINKOFF) {
                        continue;
                    }
                    
                    final var portfolio = client.getPortfolioContext ()
                        . getPortfolioCurrencies (account.getBrokerAccountId ())
                        . join ();
                    
                    sumRUB += portfolio.getCurrencies ().stream ()
                        .filter (cur -> cur.getCurrency () == Currency.RUB).findFirst ()
                        .map (CurrencyPosition::getBalance).orElse (BigDecimal.ZERO)
                        .doubleValue ();
                }
                
                final var finalSumRUB = sumRUB;
                Platform.runLater (() -> {
                    amountField.setValue (finalSumRUB);
                });
            } catch (IOException ioe) {
                log.error ("Failed to sync balance", ioe);
            }
        });
    }
    
    private void recommendPrice (IProfile profile, long days) {
        TBSBackgroundExecutor.getInstance ().runInBackground (() -> {
            try {
                final var client = TBSClient.getInstance ().getConnection (profile, new TBSLogWrapper ());
                final var time = OffsetTime.now ();
                
                final var from = TBSConstants.NOW.minusDays (days).atTime (time);
                final var to = TBSConstants.NOW.atTime (time);
                
                for (final var bond : TBSPlanner.getInstance ().getBonds ()) {
                    final var candles = client.getMarketContext ().getMarketCandles (
                        bond.getFIGI (), from, to, CandleResolution.DAY
                    ).join ();
                    
                    if (candles.isEmpty ()) { 
                        continue;
                    }
                    
                    double sum = 0, denom = 0;
                    for (final var candle : candles.get ().getCandles ()) {
                        final var off = candle.getTime ().until (to, ChronoUnit.DAYS) + 1;
                        sum += candle.getC ().doubleValue () * off;
                        denom += off;
                    }
                    
                    if (denom != 0.0) {
                        bond.setRecommendedPrice (sum / denom);
                    }
                }
                
                TBSPlanner.getInstance ().updateRecommendationsParameter (days);
            } catch (IOException ioe) {
                log.error ("Failed to calculate recommended price", ioe);
            }
        });
    }
    
}
