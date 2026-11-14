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
    private final JMenuItem imageryMenuItem;
    public DawarichPlugin(PluginInformation info) {
        super(info);
        ConnectionPreferences.Settings settings=ConnectionPreferences.load(Config.getPref(),new SavedApiKeys(org.openstreetmap.josm.io.auth.CredentialsManager.getInstance(),Config.getPref()));
        registry=settings.registry(); if(registry.rows().isEmpty())registry.add("My GPS data");
        task=new LoadReferenceTask(registry,MainApplication.getLayerManager(),MainApplication.worker);
        action=new LoadReferenceAction(registry,task,settings.startDate(),settings.endDate()); action.panel().setLoadOptions(settings.options());
        menuItem=MainMenu.add(MainApplication.getMenu().gpsMenu,action);
        imageryMenuItem=MainMenu.add(MainApplication.getMenu().imageryMenu,action);
        imageryMenuItem.setText("Check alignment with Dawarich GPS…");
        task.setListener(result->action.showResult(result));
        registry.addChangeListener(change->saveConfiguration());
        if(registry.credentialWarning()!=null)action.panel().statusLabel().setText(registry.credentialWarning());
        Lifecycle.setShutdownSequence(chain(this::destroy,Lifecycle.getShutdownSequence()));
    }
    private static Runnable chain(Runnable prior,Runnable cleanup){return ()->{try{if(prior!=null)prior.run();}finally{cleanup.run();}};}
    private void saveConfiguration(){
        var saved=ConnectionPreferences.load(Config.getPref());LoadReferenceDialog panel=action.panel();LocalDate start=saved.startDate(),end=saved.endDate();LoadOptions options=saved.options();
        try{LocalDate a=LocalDate.parse(panel.startDateField().getText()),b=LocalDate.parse(panel.endDateField().getText());if(!b.isBefore(a)&&!b.isAfter(LocalDate.now())){start=a;end=b;}}catch(RuntimeException ignored){}
        try{options=panel.loadOptions();}catch(RuntimeException ignored){}
        ConnectionPreferences.save(Config.getPref(),registry,start,end,options);
    }
    public void destroy(){
        try { LoadReferenceDialog p=action.panel(); LocalDate a=LocalDate.parse(p.startDateField().getText()),b=LocalDate.parse(p.endDateField().getText()); if(b.isBefore(a)||b.isAfter(LocalDate.now()))throw new IllegalArgumentException(); ConnectionPreferences.save(Config.getPref(),registry,a,b,p.loadOptions()); } catch(RuntimeException ignored) { try { LoadReferenceDialog p=action.panel(); var saved=ConnectionPreferences.load(Config.getPref()); ConnectionPreferences.save(Config.getPref(),registry,saved.startDate(),saved.endDate(),p.loadOptions()); }
        catch(RuntimeException fallbackFailure) { /* retain safe saved date metadata */ } }
        action.dispose();task.close();MainApplication.getMenu().gpsMenu.remove(menuItem);MainApplication.getMenu().imageryMenu.remove(imageryMenuItem);
    }
}
