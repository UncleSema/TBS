package ru.shemplo.tbs.gfx;

import static ru.shemplo.tbs.TBSConstants.*;
import static ru.shemplo.tbs.gfx.TBSStyles.*;

import java.time.LocalDate;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ru.shemplo.tbs.MappingROProperty;
import ru.shemplo.tbs.TBSBondManager;
import ru.shemplo.tbs.TBSEmitterManager;
import ru.shemplo.tbs.TBSPlanner;
import ru.shemplo.tbs.TBSUtils;
import ru.shemplo.tbs.TinkoffRequests;
import ru.shemplo.tbs.entity.BondCreditRating;
import ru.shemplo.tbs.entity.CouponValueMode;
import ru.shemplo.tbs.entity.IBond;
import ru.shemplo.tbs.entity.LinkedObject;
import ru.shemplo.tbs.entity.LinkedSymbolOrImage;
import ru.shemplo.tbs.gfx.table.TBSTableCell;
import ru.shemplo.tbs.moex.MOEXRequests;
import ru.tinkoff.invest.openapi.model.rest.Currency;

public class TBSBondsTable extends VBox {
    
    private TableView <IBond> table;
    
    public TBSBondsTable (TBSTableType type) {
        setPadding (new Insets (2, 0, 0, 0));
        getChildren ().add (makeTable (type));
        setFillWidth (true);
    }
    
    private Parent makeTable (TBSTableType type) {
        table = new TableView <> ();
        table.getStylesheets ().setAll (STYLE_TABLES);
        table.setBackground (TBSStyles.BG_TABLE);
        VBox.setVgrow (table, Priority.ALWAYS);
        table.setSelectionModel (null);
        table.setBorder (Border.EMPTY);
        
        final var grThreshold = TBSStyles.<IBond, Number> threshold (0.0, 1e-6);
        final var creditRating = TBSStyles.<IBond> creditRating ();
        final var fixedCoupons = TBSStyles.<IBond> fixedCoupons ();
        final var sameMonth = TBSStyles.<IBond> sameMonth (NOW);
        final var linkIcon = TBSStyles.<IBond> linkIcon ();
        
        table.getColumns ().add (TBSUIUtils.<IBond, LinkedSymbolOrImage> buildTBSIconTableColumn ()
            .name ("T").tooltip (null).minWidth (30.0).sortable (false)
            .propertyFetcher (b -> makeExloreProperty (b, "🌐")).highlighter (linkIcon)
            .onClick ((me, cell) -> handleExploreBrowserColumnClick (me, cell, true))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, LinkedSymbolOrImage> buildTBSIconTableColumn ()
            .name ("M").tooltip (null).minWidth (30.0).sortable (false)
            .propertyFetcher (b -> makeExloreProperty (b, "🌐")).highlighter (linkIcon)
            .onClick ((me, cell) -> handleExploreBrowserColumnClick (me, cell, false))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, LinkedSymbolOrImage> buildTBSIconTableColumn ()
            .name ("C").tooltip (null).minWidth (30.0).sortable (false)
            .propertyFetcher (b -> makeExloreProperty (b, "🔍")).highlighter (linkIcon)
            .onClick ((me, cell) -> handleExploreCouponsColumnClick (me, cell, type == TBSTableType.SCANNED))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, LinkedObject <Boolean>> buildTBSToggleTableColumn ()
            .name ("📎").tooltip (null).minWidth (30.0).sortable (false)
            .propertyFetcher (this::makePinProperty).highlighter (null)
            .onToggle (this::handlePlannerPinToggle)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, String> buildTBSTableColumn ()
            .name ("Name").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (250.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("name", () -> "")).converter ((r, v) -> v)
            .highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, String> buildTBSTableColumn ()
            .name ("Ticker").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (125.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("code", () -> "")).converter ((r, v) -> v)
            .highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, Currency> buildTBSTableColumn ()
            .name ("Currency").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("currency", null))
            .converter ((r, v) -> String.valueOf (v)).highlighter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
            .name ("👝").tooltip ("Number of lots in your portfolio (sum by all your accounts)")
            .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("lots", null))
            .highlighter (grThreshold).converter (null)
            .build ());
        if (type == TBSTableType.SCANNED) {
            table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
                .name ("Score").tooltip (null)
                .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
                .propertyFetcher (bond -> bond.getRWProperty ("score", null))
                .highlighter (grThreshold).converter (null)
                .build ());
            table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
                .name ("Credit").tooltip ("Coupons credit plus difference between inflated nominal value and price")
                .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
                .propertyFetcher (bond -> bond.getRWProperty ("pureCredit", null))
                .highlighter (grThreshold).converter (null)
                .build ());
        }   
        table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
            .name ("Coupons").tooltip ("Sum of coupons since the next coupon date with inflation")
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("couponsCredit", null))
            .highlighter (grThreshold).converter (null)
            .build ());
        if (type == TBSTableType.SCANNED) {
            table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
                .name ("Price").tooltip ("Last committed price in MOEX")
                .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
                .propertyFetcher (bond -> bond.getRWProperty ("lastPrice", null))
                .highlighter (null).converter (null)
                .build ());
        }
        table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
            .name ("Nominal").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (80.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("nominalValue", null))
            .highlighter (null).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
            .name ("C / Y").tooltip ("Coupons per year")
            .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("couponsPerYear", null))
            .highlighter (null).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, LocalDate> buildTBSTableColumn ()
            .name ("Next C R").tooltip ("Closest date to the next coupon record")
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("nextRecord", null))
            .highlighter (sameMonth).converter ((c, v) -> String.valueOf (v))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, LocalDate> buildTBSTableColumn ()
            .name ("Next C").tooltip ("Closest date to the next coupon")
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("nextCoupon", null))
            .highlighter (sameMonth).converter ((c, v) -> String.valueOf (v))
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, CouponValueMode> buildTBSTableColumn ()
            .name ("C mode").tooltip ("Coupon mode")
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> new SimpleObjectProperty <> (bond.getCouponValuesMode ()))
            .converter ((c, v) -> TBSUtils.mapIfNN (v, CouponValueMode::name, ""))
            .highlighter (fixedCoupons)
            .build ());
        if (type == TBSTableType.SCANNED) {
            table.getColumns ().add (TBSUIUtils.<IBond, Long> buildTBSTableColumn ()
                .name ("Ys").tooltip ("Years till end")
                .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
                .propertyFetcher (bond -> new SimpleObjectProperty <> (bond.getYearsToEnd ()))
                .highlighter (null).converter (null)
                .build ());
            table.getColumns ().add (TBSUIUtils.<IBond, Long> buildTBSTableColumn ()
                .name ("Ms").tooltip ("Months till end (value from range 0 to 12)")
                .alignment (Pos.BASELINE_LEFT).minWidth (50.0).sortable (false)
                .propertyFetcher (bond -> new SimpleObjectProperty <> (bond.getMonthsToEnd () % 12))
                .highlighter (null).converter (null)
                .build ());
        }
        table.getColumns ().add (TBSUIUtils.<IBond, Number> buildTBSTableColumn ()
            .name ("MOEX %").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (90.0).sortable (false)
            .propertyFetcher (bond -> bond.getRWProperty ("percentage", null))
            .highlighter (grThreshold).converter (null)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, String> buildTBSTableColumn ()
            .name ("Emitter").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (200.0).sortable (false)
            .propertyFetcher (bond -> new MappingROProperty <> (
                bond.<Long> getRWProperty ("emitterId", () -> null), 
                v -> {
                    final var emitter = TBSEmitterManager.getInstance ().getEmitterById (v);
                    if (emitter == null) { return "<Unknown emitter>"; }
                    
                    return TBSUtils.notBlank (emitter.getName ()) ? emitter.getName () 
                         : "#" + emitter.getId ();
                }
            ))
            .highlighter (null).converter ((c, v) -> v)
            .build ());
        table.getColumns ().add (TBSUIUtils.<IBond, BondCreditRating> buildTBSTableColumn ()
            .name ("Credit rating").tooltip (null)
            .alignment (Pos.BASELINE_LEFT).minWidth (100.0).sortable (false)
            .propertyFetcher (bond -> new SimpleObjectProperty <> (TBSBondManager.getBondCreditRating (bond.getCode ())))
            .converter ((c, v) -> TBSUtils.mapIfNN (v, BondCreditRating::name, ""))
            .highlighter (creditRating)
            .build ());
        
        return table;
    }
    
    private ObjectProperty <LinkedObject <Boolean>> makePinProperty (IBond bond) {
        final var planner = TBSPlanner.getInstance ();
        
        final var codePropery = bond.getRWProperty ("code", () -> "");
        final var plannerBondsProperty = planner.getBonds ();
        final var codeValue = codePropery.get ();
        
        final var UIProperty = bond.getProperty (IBond.UI_SELECTED_PROPERTY, 
            () -> new LinkedObject <> (codeValue, planner.hasBond (codeValue)), false
        );
        UIProperty.bind (Bindings.createObjectBinding (
            () -> new LinkedObject <> (codePropery.get (), planner.hasBond (codePropery.get ())), 
            codePropery, plannerBondsProperty
        ));
        
        return UIProperty;
    }
    
    private void handlePlannerPinToggle (LinkedObject <Boolean> item, Boolean selected) {
        TBSUtils.doIfNN (item, i -> {
            if (TBSUtils.aOrB (selected, false)) {
                TBSPlanner.getInstance ().addBond (i.getLink ());
            } else {
                TBSPlanner.getInstance ().removeBond (i.getLink ());
            }
        });
    }
    
    private ObjectProperty <LinkedSymbolOrImage> makeExloreProperty (IBond bond, String symbol) {
        final var codePropery = bond.getRWProperty ("code", () -> "");
        final var property = new SimpleObjectProperty <LinkedSymbolOrImage> ();
        property.bind (Bindings.createObjectBinding (
            () -> LinkedSymbolOrImage.symbol (symbol, codePropery.get ()), 
            codePropery
        ));
        return property;
    }
    
    private void handleExploreBrowserColumnClick (MouseEvent me, TBSTableCell <IBond, LinkedSymbolOrImage> cell, boolean openInTinkoff) {
        if (me.getButton () == MouseButton.PRIMARY) {
            final var ticker = cell.getItem ().getLink ();
            if (openInTinkoff) {                        
                TBSUIApplication.getInstance ().openLinkInBrowser (TinkoffRequests.makeTinkoffBondPageURL (ticker));
            } else {
                TBSUIApplication.getInstance ().openLinkInBrowser (MOEXRequests.makeMOEXBondPageURL (ticker));
            }
        }
    }
    
    private void handleExploreCouponsColumnClick (MouseEvent me, TBSTableCell <IBond, LinkedSymbolOrImage> cell, boolean scanned) {
        if (me.getButton () == MouseButton.PRIMARY && cell.getItem () != null) {
            final var bond = TBSBondManager.getBondByTicker (cell.getItem ().getLink (), scanned);
            final var scene = ((Node) me.getSource ()).getScene ();
            new TBSCouponsInspection (scene.getWindow (), bond);
        }
    }
    
    public void applyData (ObservableList <IBond> bonds) {
        table.setItems (bonds);
    }
    
}
