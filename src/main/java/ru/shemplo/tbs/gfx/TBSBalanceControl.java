package ru.shemplo.tbs.gfx;

import com.panemu.tiwulfx.control.NumberField;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import ru.shemplo.tbs.TBSBalanceController;
import ru.shemplo.tbs.entity.BalanceScale;

public class TBSBalanceControl extends ScrollPane {
    
    private XYChart <String, Number> balanceChart, creditsChart;
    
    private boolean listenersLocked = false;
    
    public TBSBalanceControl () {
        setPadding (new Insets (2, 0, 0, 0));
        setFitToHeight (true);
        setFitToWidth (true);
        setMinHeight (500);
        
        final var content = new VBox ();
        content.setPadding (new Insets (10, 16, 12, 16));
        content.setFillWidth (true);
        setContent (content);
        
        content.getChildren ().add (makeTopToolbar ());
        content.getChildren ().add (makeTopChart ());
    }
    
    private Parent makeTopToolbar () {
        final var column = new VBox (4);
        
        final var line1 = new HBox (8);
        column.getChildren ().add (line1);
        
        final var scaleHeader = new Text ("Scale");
        scaleHeader.setWrappingWidth (100.0);
        line1.getChildren ().add (scaleHeader);
        
        final var offsetHeader = new Text ("Offset");
        offsetHeader.setWrappingWidth (350.0);
        line1.getChildren ().add (offsetHeader);
        
        final var amountHeader = new Text ("Chart dates");
        amountHeader.setWrappingWidth (700.0);
        line1.getChildren ().add (amountHeader);
        
        final var line2 = new HBox (8);
        column.getChildren ().add (line2);
        
        final var defaultScale = BalanceScale.DAY;
        final var scaleSelect = new ChoiceBox <BalanceScale> ();
        scaleSelect.getItems ().setAll (BalanceScale.values ());
        scaleSelect.setMinWidth (scaleHeader.getWrappingWidth ());
        scaleSelect.setValue (defaultScale);
        line2.getChildren ().add (scaleSelect);
        
        final var offsetSlider = new Slider (
            0.0, defaultScale.getMaxOffset (), 
            defaultScale.getDefaultOffset ()
        );
        offsetSlider.setShowTickLabels (true);
        offsetSlider.setShowTickMarks (true);
        offsetSlider.setMajorTickUnit (3.0);
        offsetSlider.setMinorTickCount (2);
        offsetSlider.setSnapToTicks (true);
        offsetSlider.setMinWidth (285.0);
        line2.getChildren ().add (offsetSlider);
        
        final var offsetField = new NumberField <> ();
        offsetField.setValue (defaultScale.getDefaultOffset ());
        offsetField.setMaxWidth (
            offsetHeader.getWrappingWidth () - offsetSlider.getMinWidth () - line2.getSpacing ()
        );
        line2.getChildren ().add (offsetField);
        
        final var amountSlider = new Slider (
            defaultScale.getMinAmount (), defaultScale.getMaxAmount (), 
            defaultScale.getDefaultAmount ()
        );
        amountSlider.setShowTickLabels (true);
        amountSlider.setShowTickMarks (true);
        amountSlider.setMajorTickUnit (5.0);
        amountSlider.setMinorTickCount (4);
        amountSlider.setSnapToTicks (true);
        amountSlider.setMinWidth (635.0);
        line2.getChildren ().add (amountSlider);
        
        final var amountField = new NumberField <> ();
        amountField.setValue (defaultScale.getDefaultAmount ());
        amountField.setMaxWidth (
            amountHeader.getWrappingWidth () - amountSlider.getMinWidth () - line2.getSpacing ()
        );
        line2.getChildren ().add (amountField);
        
        scaleSelect.valueProperty ().addListener ((__, ___, scale) -> {
            listenersLocked = true;
            TBSBalanceController.getInstance ().updateParameters (scale);
            offsetSlider.setMax (scale.getMaxOffset ());
            offsetSlider.setValue (scale.getDefaultOffset ());
            amountSlider.setMin (scale.getMinAmount ());
            amountSlider.setMax (scale.getMaxAmount ());
            amountSlider.setValue (scale.getDefaultAmount ());
            listenersLocked = false;
        });
        
        final var offsetProperty = offsetSlider.valueProperty ();
        final var amountProperty = amountSlider.valueProperty ();
        
        offsetProperty.bindBidirectional (offsetField.valueProperty ());
        offsetProperty.addListener ((__, ___, offset) -> {
            if (listenersLocked) { return; }
            
            TBSBalanceController.getInstance ().updateParameters (
                scaleSelect.getValue (), offset.intValue (), 
                amountProperty.intValue ()
            );
        });
        
        amountProperty.bindBidirectional (amountField.valueProperty ());
        amountProperty.addListener ((__, ___, amount) -> {
            if (listenersLocked) { return; }
            
            TBSBalanceController.getInstance ().updateParameters (
                scaleSelect.getValue (), offsetProperty.intValue (), 
                amount.intValue ()
            );
        });
        
        return column;
    }
    
    private Parent makeTopChart () {
        final var chart = new StackPane ();
        
        final var datesAxis = new CategoryAxis ();
        final var valuesAxis = new NumberAxis ();
        valuesAxis.setAutoRanging (false);
        
        final var pBalanceSeries = TBSBalanceController.getInstance ().makeSeries (true, true); // 1
        final var balanceSeries = TBSBalanceController.getInstance ().makeSeries (false, true); // 0
        
        final var pCreditsSeries = TBSBalanceController.getInstance ().makeSeries (true, false);  // 1
        final var creditsSeries  = TBSBalanceController.getInstance ().makeSeries (false, false); // 0
        
        final var upperBound = TBSBalanceController.getInstance ().getChartUpperBound ();
        valuesAxis.upperBoundProperty ().bind (upperBound);
        valuesAxis.tickUnitProperty ().bind (Bindings.divide (upperBound, 10.0));
        
        balanceChart = new StackedAreaChart <> (datesAxis, valuesAxis);
        balanceChart.setLegendVisible (false);
        balanceChart.setAnimated (false);
        balanceChart.maxHeight (100.0);
        chart.getChildren ().add (balanceChart);
        
        balanceChart.getData ().add (balanceSeries);
        balanceChart.getData ().add (pBalanceSeries);
        
        creditsChart = new StackedBarChart <> (datesAxis, valuesAxis);
        creditsChart.getStylesheets ().add (TBSStyles.STYLE_MULTI_CHART);
        creditsChart.maxHeight (balanceChart.getMaxHeight ());
        creditsChart.setLegendVisible (false);
        creditsChart.setAnimated (false);
        chart.getChildren ().add (creditsChart);
        
        creditsChart.getData ().add (creditsSeries);
        creditsChart.getData ().add (pCreditsSeries);
        
        return chart;
    }
    
}
