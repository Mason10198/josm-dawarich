package org.openstreetmap.josm.plugins.dawarich;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/** Network-free connection panel. Load delivers a validated request to its owner. */
public final class LoadReferenceDialog extends JPanel {
    public static final class LoadRequest {
        public final ConnectionSettings settings;
        public final LocalDate startDate, endDate;
        public final ZoneId timezone;
        LoadRequest(ConnectionSettings s, LocalDate a, LocalDate b, ZoneId z){settings=s;startDate=a;endDate=b;timezone=z;}
    }
    private final JTextField server=new JTextField(28), start=new JTextField(10), end=new JTextField(10);
    private final JCheckBox http=new JCheckBox("Allow HTTP (unencrypted; trusted endpoints only)");
    private final JLabel status=new JLabel(" ");
    private final SourceRegistry registry;
    private final SourceModel model;
    private Consumer<LoadRequest> loadHandler;
    private Runnable cancelHandler;
    private final JButton loadButton=new JButton("Load this view");

    public LoadReferenceDialog(SourceRegistry registry){this(registry, LocalDate.now().minusMonths(12), LocalDate.now());}
    public LoadReferenceDialog(SourceRegistry registry, LocalDate startDate, LocalDate endDate){
        super(new BorderLayout(6,6)); this.registry=Objects.requireNonNull(registry);
        server.setText(Objects.toString(registry.endpoint(), "")); http.setSelected(registry.allowHttp());
        model=new SourceModel(); JTable table=new JTable(model); table.setRowHeight(24);
        table.getColumnModel().getColumn(2).setCellRenderer((t,v,sel,focus,r,c)->{
            JLabel label=new JLabel(Boolean.TRUE.equals(v)?"••••••••":""); label.setOpaque(true); if(sel)label.setBackground(t.getSelectionBackground()); return label;
        });
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JPasswordField()){
            @Override public Component getTableCellEditorComponent(JTable t,Object value,boolean selected,int row,int col){return super.getTableCellEditorComponent(t,"",selected,row,col);}
            @Override public Object getCellEditorValue(){char[] chars=((JPasswordField)getComponent()).getPassword(); try{return new String(chars);}finally{Arrays.fill(chars,'\0');}}
        });
        table.getColumnModel().getColumn(3).setCellRenderer((t,v,sel,focus,r,c)->{JLabel x=new JLabel("  "+v);x.setOpaque(true);x.setBackground(sel?t.getSelectionBackground():Color.WHITE);try{x.setForeground(Color.decode(v.toString()));}catch(Exception ignored){}return x;});
        JPanel form=new JPanel(new GridLayout(0,1));form.add(new JLabel("Dawarich server URL"));form.add(server);form.add(http);
        form.add(new JLabel("Start and end dates (YYYY-MM-DD); timezone: "+ZoneId.systemDefault())); JPanel dates=new JPanel();dates.add(start);dates.add(end);form.add(dates);form.add(status);
        add(form,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);
        JButton add=new JButton("Add source"), remove=new JButton("Remove selected"), color=new JButton("Choose color"), cancel=new JButton("Cancel");
        JPanel buttons=new JPanel();buttons.add(add);buttons.add(remove);buttons.add(color);buttons.add(cancel);buttons.add(loadButton);add(buttons,BorderLayout.SOUTH);
        add.addActionListener(e->{registry.add("Source "+(registry.rows().size()+1));model.fireTableDataChanged();});
        remove.addActionListener(e->{int r=table.getSelectedRow();if(r>=0){registry.remove(registry.rows().get(r).id);model.fireTableDataChanged();}});
        color.addActionListener(e->{int r=table.getSelectedRow();if(r>=0){SourceSettings s=registry.rows().get(r);Color c=JColorChooser.showDialog(this,"Source color",Color.decode(s.color));if(c!=null)registry.updateSettings(new SourceSettings(s.id,s.label,String.format("#%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue()),s.enabled));model.fireTableDataChanged();}});
        server.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){http.setSelected(false);}public void removeUpdate(javax.swing.event.DocumentEvent e){http.setSelected(false);}public void changedUpdate(javax.swing.event.DocumentEvent e){http.setSelected(false);}});
        start.setText(Objects.requireNonNull(startDate).toString());end.setText(Objects.requireNonNull(endDate).toString());
        loadButton.setEnabled(false);
        cancel.addActionListener(e->{clearSecretEditors();if(cancelHandler!=null)cancelHandler.run();});
        loadButton.addActionListener(e->{status.setText(" ");LoadRequest request;try{validateAndCommit();request=new LoadRequest(ConnectionSettings.parse(server.getText(),http.isSelected()),LocalDate.parse(start.getText()),LocalDate.parse(end.getText()),ZoneId.systemDefault());}catch(IllegalArgumentException ex){status.setText("Invalid connection, dates, or enabled-source credentials");return;}loadHandler.accept(request);});
    }
    public JTable sourceTable(){return (JTable)((JScrollPane)getComponent(1)).getViewport().getView();}
    public JButton loadButton(){return loadButton;}
    public JTextField startDateField(){return start;} public JTextField endDateField(){return end;}
    public JTextField serverField(){return server;} public JCheckBox httpCheckBox(){return http;} public JLabel statusLabel(){return status;}
    public void setLoadHandler(Consumer<LoadRequest> handler){loadHandler=Objects.requireNonNull(handler);loadButton.setEnabled(true);}
    public void setCancelHandler(Runnable handler){cancelHandler=Objects.requireNonNull(handler);}
    public void validateAndCommit(){
        if(sourceTable().isEditing()&&!sourceTable().getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish editing source settings");
        LocalDate a,b;try{a=LocalDate.parse(start.getText());b=LocalDate.parse(end.getText());}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid date range",ex);}
        if(b.isBefore(a)||b.isAfter(LocalDate.now()))throw new IllegalArgumentException("Invalid date range");
        ConnectionSettings parsed=ConnectionSettings.parse(server.getText(),http.isSelected());registry.setEndpoint(parsed.baseUrl(),http.isSelected());model.fireTableDataChanged();
        boolean enabled=false;for(SourceSettings s:registry.rows()){if(s.enabled){enabled=true;if(Objects.toString(registry.session(s.id).snapshot().apiKey(),"").isBlank())throw new IllegalArgumentException("Enabled sources require API keys");}}
        if(!enabled)throw new IllegalArgumentException("Enable at least one source");
    }
    public void clearSecretEditors(){if(sourceTable().isEditing()&&sourceTable().getEditorComponent() instanceof JPasswordField p){char[] c=p.getPassword();Arrays.fill(c,'\0');p.setText("");sourceTable().getCellEditor().cancelCellEditing();}}
    private final class SourceModel extends AbstractTableModel {
        private final String[] cols={"Enabled","Source","API key","Color"};
        public int getRowCount(){return registry.rows().size();}public int getColumnCount(){return 4;}public String getColumnName(int c){return cols[c];}
        public Class<?> getColumnClass(int c){return c==0?Boolean.class:c==2?Boolean.class:String.class;}
        public Object getValueAt(int r,int c){SourceSettings s=registry.rows().get(r);return switch(c){case 0->s.enabled;case 1->s.label;case 2->!Objects.toString(registry.session(s.id).snapshot().apiKey(),"").isEmpty();default->s.color;};}
        public boolean isCellEditable(int r,int c){return c<3;}
        public void setValueAt(Object v,int r,int c){SourceSettings s=registry.rows().get(r);switch(c){case 0->registry.setEnabled(s.id,Boolean.TRUE.equals(v));case 1->registry.updateSettings(new SourceSettings(s.id,Objects.toString(v),s.color,s.enabled));case 2->{String key=Objects.toString(v,"");if(!key.isEmpty()){if(!server.getText().isBlank()){ConnectionSettings parsed=ConnectionSettings.parse(server.getText(),http.isSelected());registry.setEndpoint(parsed.baseUrl(),http.isSelected());}registry.setKey(s.id,key);model.fireTableDataChanged();return;}}default->{}} fireTableRowsUpdated(r,r);}
    }
}
