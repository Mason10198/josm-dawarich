package org.openstreetmap.josm.plugins.dawarich;

import java.awt.*;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/** Network-free guided setup and transfer feedback for private imagery comparison. */
public final class LoadReferenceDialog extends JPanel {
    public static final class LoadRequest {
        public final ConnectionSettings settings;
        public final LocalDate startDate, endDate;
        public final ZoneId timezone;
        public final LoadOptions options;
        LoadRequest(ConnectionSettings s,LocalDate a,LocalDate b,ZoneId z){this(s,a,b,z,LoadOptions.defaults());}
        LoadRequest(ConnectionSettings s,LocalDate a,LocalDate b,ZoneId z,LoadOptions o){settings=s;startDate=a;endDate=b;timezone=z;options=o;}
    }
    private static final class FormProblem extends IllegalArgumentException {
        FormProblem(String message){super(message);}
    }
    private final JTextField server=new JTextField(28),start=new JTextField(10),end=new JTextField(10);
    private final JPasswordField apiKey=new JPasswordField(24);
    private final JLabel keyFor=new JLabel(),keyState=new JLabel(),status=new JLabel(" "),progressLabel=new JLabel(" ");
    private final JComboBox<LoadOptions.Mode> mode=new JComboBox<>(LoadOptions.Mode.values());
    private final JSpinner recentCount=new JSpinner(new SpinnerNumberModel(10000,1,LoadOptions.MAX_TOTAL_POINTS,1000));
    private final JSpinner totalBudget=new JSpinner(new SpinnerNumberModel(50000,1,LoadOptions.MAX_TOTAL_POINTS,1000));
    private final JCheckBox http=new JCheckBox("Allow unencrypted HTTP (trusted servers only)");
    private final JToggleButton advancedToggle=new JToggleButton("Advanced settings");
    private final JProgressBar progressBar=new JProgressBar();
    private final JButton loadButton=new JButton("Load GPS for this area"),cancelButton=new JButton("Stop download"),closeButton=new JButton("Close");
    private final JButton useKey=new JButton("Use this key"),addSource=new JButton("Add person/device"),removeSource=new JButton("Remove selected"),chooseColor=new JButton("Choose color");
    private final SourceRegistry registry;
    private final SourceModel model;
    private final JTable table;
    private Consumer<LoadRequest> loadHandler;
    private Runnable cancelHandler,closeHandler;
    private boolean loading,refreshing;

    public LoadReferenceDialog(SourceRegistry registry){this(registry,LocalDate.now().minusMonths(12),LocalDate.now());}
    public LoadReferenceDialog(SourceRegistry registry,LocalDate startDate,LocalDate endDate){
        super(new BorderLayout(8,8));this.registry=Objects.requireNonNull(registry);
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        server.setText(Objects.toString(registry.endpoint(),""));http.setSelected(registry.allowHttp());
        start.setText(Objects.requireNonNull(startDate).toString());end.setText(Objects.requireNonNull(endDate).toString());
        model=new SourceModel();table=new JTable(model);table.setRowHeight(24);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(new Dimension(580,65));
        table.getColumnModel().getColumn(2).setCellRenderer((t,v,selected,focus,row,col)->{
            JLabel label=new JLabel(Boolean.TRUE.equals(v)?"••••••••":"Key needed");label.setOpaque(true);
            label.setBackground(selected?t.getSelectionBackground():t.getBackground());return label;
        });
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JPasswordField()){
            @Override public Component getTableCellEditorComponent(JTable t,Object value,boolean selected,int row,int col){return super.getTableCellEditorComponent(t,"",selected,row,col);}
            @Override public Object getCellEditorValue(){char[] chars=((JPasswordField)getComponent()).getPassword();try{return new String(chars);}finally{Arrays.fill(chars,'\0');}}
        });
        table.getColumnModel().getColumn(3).setCellRenderer((t,v,selected,focus,row,col)->{
            JLabel label=new JLabel("  "+v);label.setOpaque(true);label.setBackground(selected?t.getSelectionBackground():t.getBackground());
            try{label.setForeground(Color.decode(v.toString()));}catch(RuntimeException ignored){}return label;
        });
        mode.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                return super.getListCellRendererComponent(list,value==LoadOptions.Mode.MOST_RECENT?"Most recent points (recommended)":"Date range",index,selected,focus);
            }
        });
        add(new JLabel("<html><b>Check aerial imagery alignment with your GPS</b><br>Open aerial imagery in JOSM, zoom to the area you want to check, then load GPS below.</html>"),BorderLayout.NORTH);
        JPanel body=new ScrollableBody();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        body.add(connectionPanel());body.add(Box.createVerticalStrut(6));body.add(selectionPanel());
        advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);body.add(advancedToggle);
        JPanel advanced=new JPanel(new GridBagLayout());advanced.setAlignmentX(Component.LEFT_ALIGNMENT);advanced.setBorder(BorderFactory.createTitledBorder("Advanced — optional"));
        addRow(advanced,0,new JLabel("Total point budget (1–250,000)"),totalBudget);addRow(advanced,1,http,null);
        addRow(advanced,2,new JLabel("Hidden GPS layers also use this budget. Higher budgets may be slower."),null);
        advanced.setVisible(false);body.add(advanced);
        advancedToggle.addActionListener(e->{advanced.setVisible(advancedToggle.isSelected());body.revalidate();});
        JScrollPane bodyScroll=new JScrollPane(body,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());add(bodyScroll,BorderLayout.CENTER);
        JPanel feedback=new JPanel(new BorderLayout(4,4));JPanel activity=new JPanel();activity.setLayout(new BoxLayout(activity,BoxLayout.Y_AXIS));
        activity.add(progressLabel);activity.add(progressBar);feedback.add(activity,BorderLayout.NORTH);
        JScrollPane results=new JScrollPane(status);results.setPreferredSize(new Dimension(580,92));results.setMinimumSize(new Dimension(0,65));feedback.add(results,BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));buttons.add(cancelButton);buttons.add(closeButton);buttons.add(loadButton);feedback.add(buttons,BorderLayout.SOUTH);add(feedback,BorderLayout.SOUTH);
        progressBar.setStringPainted(false);progressBar.setVisible(false);cancelButton.setEnabled(false);loadButton.setEnabled(false);
        if(model.getRowCount()>0)table.setRowSelectionInterval(0,0);updateKeyState();updateModeControls();
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!refreshing){clearVisibleKey();updateKeyState();}});
        server.getDocument().addDocumentListener(new DocumentListener(){
            private void changed(){http.setSelected(false);clearVisibleKey();}
            public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}
        });
        mode.addActionListener(e->updateModeControls());
        useKey.addActionListener(e->{try{commitVisibleKey(true);status.setText("API key is ready for this session. Choose your points and load GPS.");}catch(FormProblem ex){status.setText(ex.getMessage());}});
        apiKey.addActionListener(e->useKey.doClick());
        addSource.addActionListener(e->{SourceSettings row=registry.add("Person "+(registry.rows().size()+1));refreshSources(row.id);clearVisibleKey();});
        removeSource.addActionListener(e->{String id=selectedId();if(id!=null){registry.remove(id);clearVisibleKey();refreshSources(null);}});
        chooseColor.addActionListener(e->{String id=selectedId();if(id==null)return;SourceSettings row=registry.rows().stream().filter(s->s.id.equals(id)).findFirst().orElseThrow();Color color=JColorChooser.showDialog(this,"GPS source color",Color.decode(row.color));if(color!=null){registry.updateSettings(new SourceSettings(id,row.label,String.format("#%02X%02X%02X",color.getRed(),color.getGreen(),color.getBlue()),row.enabled));refreshSources(id);}});
        loadButton.addActionListener(e->load());
        cancelButton.addActionListener(e->{clearSecretEditors();if(cancelHandler!=null)cancelHandler.run();});
        closeButton.addActionListener(e->{clearSecretEditors();if(closeHandler!=null)closeHandler.run();});
        setPreferredSize(new Dimension(700,600));
    }
    private static final class ScrollableBody extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(24,visible.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private JPanel connectionPanel(){
        JPanel panel=new JPanel(new GridBagLayout());panel.setAlignmentX(Component.LEFT_ALIGNMENT);panel.setBorder(BorderFactory.createTitledBorder("1. Connect your GPS sources"));
        addRow(panel,0,new JLabel("Dawarich server address"),null);addRow(panel,1,server,null);
        server.setToolTipText("Your server's base address, such as https://gps.example.org — do not put an API key here");
        addRow(panel,2,new JLabel("Each enabled person/device needs a separate API key. Select one below."),null);
        addRow(panel,3,new JScrollPane(table),null);JPanel sourceButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));sourceButtons.add(addSource);sourceButtons.add(removeSource);sourceButtons.add(chooseColor);addRow(panel,4,sourceButtons,null);
        addRow(panel,5,keyFor,keyState);JPanel keyLine=new JPanel(new BorderLayout(6,0));keyLine.add(apiKey,BorderLayout.CENTER);keyLine.add(useKey,BorderLayout.EAST);addRow(panel,6,keyLine,null);
        addRow(panel,7,new JLabel("Paste a key and click Load. Keys stay in memory until JOSM closes."),null);return panel;
    }
    private JPanel selectionPanel(){
        JPanel panel=new JPanel(new GridBagLayout());panel.setAlignmentX(Component.LEFT_ALIGNMENT);panel.setBorder(BorderFactory.createTitledBorder("2. Choose GPS history for this map area"));
        addRow(panel,0,new JLabel("Load"),mode);addRow(panel,1,new JLabel("Recent points per person/device"),recentCount);
        JPanel dates=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));dates.add(start);dates.add(new JLabel("to"));dates.add(end);addRow(panel,2,new JLabel("Dates (YYYY-MM-DD)"),dates);
        addRow(panel,3,new JLabel("Date ranges use "+ZoneId.systemDefault()+". Recent mode ignores the dates."),null);
        addRow(panel,4,new JLabel("<html>3. Compare repeated GPS observations with imagery.<br>Use JOSM's imagery-offset tool; do not move GPS or OSM objects to match imagery.</html>"),null);return panel;
    }
    private static void addRow(JPanel panel,int row,Component left,Component right){
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=row;g.insets=new Insets(3,5,3,5);g.anchor=GridBagConstraints.WEST;g.fill=GridBagConstraints.HORIZONTAL;g.weightx=right==null?1:0;g.gridwidth=right==null?2:1;panel.add(left,g);
        if(right!=null){g.gridx=1;g.gridwidth=1;g.weightx=1;panel.add(right,g);}
    }
    private void updateModeControls(){boolean recent=mode.getSelectedItem()==LoadOptions.Mode.MOST_RECENT;start.setEnabled(!loading&&!recent);end.setEnabled(!loading&&!recent);recentCount.setEnabled(!loading&&recent);}
    private String selectedId(){int row=table.getSelectedRow();return row>=0&&row<registry.rows().size()?registry.rows().get(row).id:null;}
    private void updateKeyState(){String id=selectedId();keyFor.setText("API key for: "+sourceLabel(id));boolean has=id!=null&&registry.session(id)!=null&&registry.session(id).snapshot().apiKey()!=null;keyState.setText(has?"Key ready for this session":"Key needed");}
    private void clearVisibleKey(){char[] chars=apiKey.getPassword();Arrays.fill(chars,'\0');apiKey.setText("");}
    private void refreshSources(String preferred){
        refreshing=true;try{model.fireTableDataChanged();int row=0;for(int i=0;i<registry.rows().size();i++)if(registry.rows().get(i).id.equals(preferred))row=i;if(model.getRowCount()>0)table.setRowSelectionInterval(row,row);}finally{refreshing=false;}updateKeyState();
    }
    private ConnectionSettings parsedServer(){try{return ConnectionSettings.parse(server.getText(),http.isSelected());}catch(RuntimeException ex){throw new FormProblem("Enter a valid Dawarich server address, for example https://gps.example.org.");}}
    private void commitVisibleKey(boolean required){
        char[] chars=apiKey.getPassword();try{
            if(chars.length==0){if(required)throw new FormProblem("Paste an API key for the selected person/device.");return;}
            String key=new String(chars);if(key.isBlank())throw new FormProblem("Paste a non-empty API key for the selected person/device.");String id=selectedId();if(id==null)throw new FormProblem("Select a person/device before entering its API key.");
            ConnectionSettings parsed=parsedServer();registry.setEndpoint(parsed.baseUrl(),http.isSelected());
            try{registry.setKey(id,key);}catch(RuntimeException ex){throw new FormProblem("Use a different, valid API key for each person/device.");}
            clearVisibleKey();refreshSources(id);
        }finally{Arrays.fill(chars,'\0');}
    }
    private void load(){
        status.setText(" ");LoadRequest request;
        try{validateAndCommit();LoadOptions options=loadOptions();LocalDate first=LocalDate.now().minusMonths(12),last=LocalDate.now();if(options.mode()==LoadOptions.Mode.DATE_RANGE){first=LocalDate.parse(start.getText());last=LocalDate.parse(end.getText());}if(loadHandler==null)throw new FormProblem("Loading is not available yet.");request=new LoadRequest(parsedServer(),first,last,ZoneId.systemDefault(),options);}
        catch(FormProblem ex){status.setText(ex.getMessage());return;}
        loadHandler.accept(request);
    }
    public void validateAndCommit(){
        if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new FormProblem("Finish editing the person/device settings.");
        LoadOptions options=loadOptions();if(options.mode()==LoadOptions.Mode.DATE_RANGE){LocalDate first,last;try{first=LocalDate.parse(start.getText());last=LocalDate.parse(end.getText());}catch(RuntimeException ex){throw new FormProblem("Enter dates in YYYY-MM-DD format.");}if(last.isBefore(first)||last.isAfter(LocalDate.now()))throw new FormProblem("Choose an end date on or after the start date, and not in the future.");}
        ConnectionSettings parsed=parsedServer();registry.setEndpoint(parsed.baseUrl(),http.isSelected());commitVisibleKey(false);
        boolean enabled=false;for(SourceSettings row:registry.rows())if(row.enabled){enabled=true;if(Objects.toString(registry.session(row.id).snapshot().apiKey(),"").isBlank())throw new FormProblem("Paste an API key for each enabled person/device, or disable the ones you do not need.");}
        if(!enabled)throw new FormProblem("Enable at least one person/device.");updateKeyState();
    }
    public LoadOptions loadOptions(){
        integerValue(recentCount);integerValue(totalBudget);
        for(JSpinner spinner:new JSpinner[]{recentCount,totalBudget}){String text=((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().getText().trim();ParsePosition position=new ParsePosition(0);Number number=java.text.NumberFormat.getIntegerInstance().parse(text,position);if(number==null||position.getIndex()!=text.length())throw new FormProblem("Enter whole-number point limits.");}
        try{recentCount.commitEdit();totalBudget.commitEdit();}catch(ParseException ex){throw new FormProblem("Enter whole-number point limits.");}
        try{return new LoadOptions((LoadOptions.Mode)mode.getSelectedItem(),integerValue(recentCount),integerValue(totalBudget));}catch(IllegalArgumentException ex){throw new FormProblem("Recent points per person must fit within the total point budget in Advanced settings.");}
    }
    private static int integerValue(JSpinner spinner){Object value=spinner.getValue();if(!(value instanceof Number number))throw new FormProblem("Enter whole-number point limits.");double n=number.doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<1||n>LoadOptions.MAX_TOTAL_POINTS)throw new FormProblem("Point limits must be whole numbers between 1 and 250,000.");return(int)n;}
    public void setLoadOptions(LoadOptions options){mode.setSelectedItem(options.mode());recentCount.setValue(options.recentPointsPerSource());totalBudget.setValue(options.totalPointLimit());updateModeControls();}
    public void clearSecretEditors(){clearVisibleKey();if(table.isEditing()&&table.getEditorComponent() instanceof JPasswordField password){char[] chars=password.getPassword();Arrays.fill(chars,'\0');password.setText("");table.getCellEditor().cancelCellEditing();}}
    public void setLoading(boolean active){
        boolean changed=loading!=active;loading=active;loadButton.setEnabled(!active&&loadHandler!=null);cancelButton.setEnabled(active);
        for(Component component:new Component[]{server,apiKey,useKey,table,mode,totalBudget,http,advancedToggle,addSource,removeSource,chooseColor})component.setEnabled(!active);updateModeControls();
        if(changed&&active){progressBar.setVisible(true);progressBar.setIndeterminate(true);progressLabel.setText("Waiting for GPS data… elapsed time and received bytes will appear here.");}
        if(!active){progressBar.setIndeterminate(false);progressBar.setVisible(false);progressLabel.setText(" ");}
    }
    public void updateProgress(LoadProgress progress){
        String source=sourceLabel(progress.sourceId());String count=java.text.NumberFormat.getIntegerInstance().format(progress.selectedPoints());
        progressLabel.setText("<html>Loading "+escape(source)+" — source "+progress.sourceIndex()+" of "+progress.sourceCount()+"<br>"+humanBytes(progress.downloadedBytes())+" received · "+progress.requests()+" requests · "+count+" points selected"+(progress.requestedPoints()>0?" / "+java.text.NumberFormat.getIntegerInstance().format(progress.requestedPoints()):"")+" · "+(progress.elapsedMillis()/1000)+" s elapsed</html>");
        progressBar.setVisible(true);if(progress.requestedPoints()>0&&progress.selectedPoints()>0){progressBar.setIndeterminate(false);progressBar.setMaximum(progress.requestedPoints());progressBar.setValue((int)Math.min(progress.requestedPoints(),progress.selectedPoints()));}else progressBar.setIndeterminate(true);
    }
    private static String humanBytes(long n){if(n<1024)return n+" B";if(n<1024L*1024)return String.format(Locale.ROOT,"%.1f KiB",n/1024.0);return String.format(Locale.ROOT,"%.1f MiB",n/(1024.0*1024));}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public JTable sourceTable(){return table;}public JButton loadButton(){return loadButton;}public JButton closeButton(){return closeButton;}public JButton cancelButton(){return cancelButton;}public JButton applyKeyButton(){return useKey;}
    public JTextField startDateField(){return start;}public JTextField endDateField(){return end;}public JTextField serverField(){return server;}public JCheckBox httpCheckBox(){return http;}
    public JComboBox<LoadOptions.Mode> modeSelector(){return mode;}public JSpinner recentCountSpinner(){return recentCount;}public JSpinner totalBudgetSpinner(){return totalBudget;}public JToggleButton advancedToggle(){return advancedToggle;}
    public JPasswordField apiKeyField(){return apiKey;}public JLabel statusLabel(){return status;}public JProgressBar progressBar(){return progressBar;}public JLabel progressLabel(){return progressLabel;}
    String sourceLabel(String id){return registry.rows().stream().filter(row->row.id.equals(id)).map(row->row.label).findFirst().orElse("selected person/device");}
    public void setLoadHandler(Consumer<LoadRequest> handler){loadHandler=Objects.requireNonNull(handler);loadButton.setEnabled(!loading);}public void setCancelHandler(Runnable handler){cancelHandler=Objects.requireNonNull(handler);}public void setCloseHandler(Runnable handler){closeHandler=Objects.requireNonNull(handler);}
    private final class SourceModel extends AbstractTableModel {
        private final String[] names={"Use","Person / device","Key for this session","Color"};
        public int getRowCount(){return registry.rows().size();}public int getColumnCount(){return names.length;}public String getColumnName(int column){return names[column];}public Class<?> getColumnClass(int column){return column==0||column==2?Boolean.class:String.class;}
        public Object getValueAt(int row,int column){SourceSettings source=registry.rows().get(row);return switch(column){case 0->source.enabled;case 1->source.label;case 2->!Objects.toString(registry.session(source.id).snapshot().apiKey(),"").isBlank();default->source.color;};}
        public boolean isCellEditable(int row,int column){return !loading&&column<3;}
        public void setValueAt(Object value,int row,int column){SourceSettings source=registry.rows().get(row);switch(column){case 0->registry.setEnabled(source.id,Boolean.TRUE.equals(value));case 1->registry.updateSettings(new SourceSettings(source.id,Objects.toString(value),source.color,source.enabled));case 2->{String key=Objects.toString(value,"");if(!key.isEmpty()){ConnectionSettings parsed=parsedServer();registry.setEndpoint(parsed.baseUrl(),http.isSelected());try{registry.setKey(source.id,key);}catch(RuntimeException ex){throw new FormProblem("Use a different, valid API key for each person/device.");}refreshSources(source.id);return;}}default->{}}fireTableRowsUpdated(row,row);updateKeyState();}
    }
}
