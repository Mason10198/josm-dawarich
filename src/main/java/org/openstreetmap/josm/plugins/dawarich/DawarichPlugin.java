package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import javax.swing.JMenuItem;
import org.openstreetmap.josm.spi.lifecycle.Lifecycle;
import org.openstreetmap.josm.spi.preferences.Config;

/** Menu, preferences, and coordinator lifecycle adapter. */
public final class DawarichPlugin extends Plugin {
    private final SourceRegistry registry;
    private final LoadReferenceTask task;
    private final LoadReferenceAction action;
    private final JMenuItem menuItem;
    public DawarichPlugin(PluginInformation info) {
        super(info);
        ConnectionPreferences.Settings settings=ConnectionPreferences.load(Config.getPref());
        registry=settings.registry(); if(registry.rows().isEmpty())registry.add("Dawarich source");
        task=new LoadReferenceTask(registry,MainApplication.getLayerManager(),MainApplication.worker);
        action=new LoadReferenceAction(registry,task,settings.startDate(),settings.endDate());
        menuItem=MainMenu.add(MainApplication.getMenu().gpsMenu,action);
        task.setListener(result->action.showResult(result));
        Lifecycle.setShutdownSequence(chain(this::destroy,Lifecycle.getShutdownSequence()));
    }
    private static Runnable chain(Runnable prior,Runnable cleanup){return ()->{try{if(prior!=null)prior.run();}finally{cleanup.run();}};}
    public void destroy(){
        try { LoadReferenceDialog p=action.panel();LocalDate a=LocalDate.parse(p.startDateField().getText()),b=LocalDate.parse(p.endDateField().getText()); if(!b.isBefore(a)&&!b.isAfter(LocalDate.now()))ConnectionPreferences.save(Config.getPref(),registry,a,b); }
        catch(RuntimeException ignored) { /* invalid date metadata is not persisted */ }
        action.dispose();task.close();MainApplication.getMenu().gpsMenu.remove(menuItem);
    }
}
