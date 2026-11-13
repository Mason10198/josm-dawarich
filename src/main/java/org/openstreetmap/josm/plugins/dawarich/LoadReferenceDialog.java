package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

/** Compact, network-free dialog; callers consume its validated snapshot on Load. */
public final class LoadReferenceDialog extends JPanel {
    private final JTextField server=new JTextField(28), start=new JTextField(10), end=new JTextField(10);
    private final JCheckBox http=new JCheckBox("Allow HTTP (unencrypted; use only for a trusted endpoint)");
    private final SourceRegistry registry;
    private final JTable table;
    public LoadReferenceDialog(SourceRegistry registry){
        super(new BorderLayout(6,6)); this.registry=Objects.requireNonNull(registry);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Enabled","Source","API key","Color"},0){
            public boolean isCellEditable(int r,int c){return c<3;}
            public Class<?> getColumnClass(int c){return c==0?Boolean.class:String.class;}
        };
        table=new JTable(model); table.getColumnModel().getColumn(2).setCellRenderer((t,v,sel,focus,r,c)->new JLabel(v==null||v.toString().isEmpty()?"":"••••••••"));
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JPasswordField()){
            public Object getCellEditorValue(){return new String(((JPasswordField)getComponent()).getPassword());}
            public boolean stopCellEditing(){char[] p=((JPasswordField)getComponent()).getPassword();return super.stopCellEditing();}
        });
        JPanel form=new JPanel(new GridLayout(0,1)); form.add(new JLabel("Dawarich server URL"));form.add(server);form.add(http);
        form.add(new JLabel("Start date / end date (YYYY-MM-DD); timezone: "+ZoneId.systemDefault()));
        JPanel dates=new JPanel();dates.add(start);dates.add(end);form.add(dates);
        add(form,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel buttons=new JPanel(); JButton add=new JButton("Add source"), remove=new JButton("Remove selected");buttons.add(add);buttons.add(remove);add(buttons,BorderLayout.SOUTH);
        add.addActionListener(e->{SourceSettings row=registry.add("Source "+(registry.rows().size()+1));model.addRow(new Object[]{true,row.label,"",row.color});});
        remove.addActionListener(e->{int r=table.getSelectedRow();if(r>=0){if(table.isEditing())table.getCellEditor().stopCellEditing();if(r<registry.rows().size())registry.remove(registry.rows().get(r).id);model.removeRow(r);}});
        LocalDate today=LocalDate.now();start.setText(today.minusMonths(12).toString());end.setText(today.toString());
    }
    public JTable sourceTable(){return table;}
    public void clearSecretEditors(){if(table.isEditing()&&table.getEditorComponent() instanceof JPasswordField p){p.setText("");table.getCellEditor().cancelCellEditing();}}
    public void validateAndCommit(){
        ConnectionSettings.parse(server.getText(),http.isSelected()); registry.setEndpoint(server.getText(),http.isSelected());
        LocalDate.parse(start.getText());LocalDate.parse(end.getText());
        for(int i=0;i<table.getRowCount()&&i<registry.rows().size();i++){
            SourceSettings row=registry.rows().get(i);registry.setEnabled(row.id,Boolean.TRUE.equals(table.getValueAt(i,0)));
            Object key=table.getValueAt(i,2);if(key!=null&&!key.toString().isEmpty())registry.setKey(row.id,key.toString());
        }
    }
}
