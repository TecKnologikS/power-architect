package ca.sqlpower.architect.swingui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import ca.sqlpower.sql.DBConnectionSpec;

public class DBCSPanel extends JPanel implements ArchitectPanel {

	private static final Logger logger = Logger.getLogger(DBCSPanel.class);

	protected DBConnectionSpec dbcs;
	protected TextPanel form;

	protected JTextField dbNameField;
	protected String dbNameTemp;
	protected JComboBox dbDriverField;
	protected JComponent platformSpecificOptions;
	protected JTextField dbUrlField;
	protected JTextField dbUserField;
	protected JPasswordField dbPassField;

	private Map jdbcDrivers;

	private JDBCURLUpdater urlUpdater = new JDBCURLUpdater();
	
	private boolean updatingUrlFromFields = false;
	private boolean updatingFieldsFromUrl = false;
	
	public DBCSPanel() {
		setLayout(new BorderLayout());
		ArchitectFrame af = ArchitectFrame.getMainInstance();

		dbDriverField = new JComboBox(getDriverClasses());
		dbDriverField.insertItemAt("", 0);
		dbNameField = new JTextField();
		
		platformSpecificOptions = new JPanel();
		platformSpecificOptions.setLayout(new PlatformOptionsLayout());
		platformSpecificOptions.setBorder(BorderFactory.createEmptyBorder());
		platformSpecificOptions.add(new JLabel("(No options for current driver)"));
		
		JComponent[] fields = new JComponent[] {dbNameField,
												dbDriverField,
												platformSpecificOptions,
												dbUrlField = new JTextField(),
												dbUserField = new JTextField(),
												dbPassField = new JPasswordField()};
		String[] labels = new String[] {"Connection Name",
										"JDBC Driver",
										"Connect Options",
										"JDBC URL",
										"Username",
										"Password"};

		char[] mnemonics = new char[] {'n', 'd', 'o', 'u', 'r', 'p'};
		int[] widths = new int[] {30, 30, 40, 20, 20};
		String[] tips = new String[] {"Your nickname for this database",
									  "The class name of the JDBC Driver",
									  "Connection parameters specific to this driver",
									  "Vendor-specific JDBC URL",
									  "Username for this database",
									  "Password for this database"};
		
		// update url field when user picks new driver
		dbDriverField.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        createFieldsFromTemplate();
		        updateUrlFromFields();
		    }
		});
		
		dbUrlField.getDocument().addDocumentListener(new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
		        updateFieldsFromUrl();
            }

            public void removeUpdate(DocumentEvent e) {
		        updateFieldsFromUrl();
            }

            public void changedUpdate(DocumentEvent e) {
		        updateFieldsFromUrl();
            }
		});
		
		form = new TextPanel(fields, labels, mnemonics, widths, tips);
		add(form, BorderLayout.CENTER);
	}

	/** Returns all of the driver class names this dialog knows about. */
	protected String[] getDriverClasses() {
		if (jdbcDrivers == null) {
			setupDriverMap();
		}
		return (String[]) jdbcDrivers.keySet().toArray(new String[0]);
	}
	
	/** Returns the JDBC URL template associated with the named driver. */
	protected String getTemplateForDriver(String driverClassName) {
		if (jdbcDrivers == null) {
			setupDriverMap();
		}
		return (String) jdbcDrivers.get(driverClassName);
	}

	/**
	 * Sets up the platformSpecificOptions component to contain labels and 
	 * text fields associated with each variable in the current template.
	 */
	private void createFieldsFromTemplate() {
        for (int i = 0; i < platformSpecificOptions.getComponentCount(); i++) {
            Component c = platformSpecificOptions.getComponent(i);
            if (c instanceof JTextField) {
                ((JTextField) c).getDocument().removeDocumentListener(urlUpdater);
            }
        }
        platformSpecificOptions.removeAll();

	    String driverClassName = dbDriverField.getSelectedItem().toString();
	    String template = getTemplateForDriver(driverClassName);
	    if (template != null) {
	        Pattern varPattern = Pattern.compile("<(.*?)>");
	        Matcher varMatcher = varPattern.matcher(template);
	        List templateVars = new ArrayList();
	        while (varMatcher.find()) {
	            templateVars.add(varMatcher.group(1));
	        }
	        logger.debug("Found variables: "+templateVars);
	        	        
	        Iterator it = templateVars.iterator();
	        while (it.hasNext()) {
	            String var = (String) it.next();
	            String def = "";
	            if (var.indexOf(':') != -1) {
	                int i = var.indexOf(':');
	                def = var.substring(i+1);
	                var = var.substring(0, i);
	            }
	            
	            platformSpecificOptions.add(new JLabel(var));
	            JTextField field = new JTextField(def);
	            platformSpecificOptions.add(field);
	            field.getDocument().addDocumentListener(urlUpdater);
	        }
	        platformSpecificOptions.revalidate();
	        platformSpecificOptions.repaint();
	    } else {
	        platformSpecificOptions.add(new JLabel("Unknown driver class.  Fill in URL manually."));
	    }
	}

	private void setupDriverMap() {
		Map drivers = new HashMap();
		drivers.put("oracle.jdbc.driver.OracleDriver",
					"jdbc:oracle:thin:@<Hostname>:<Port:1521>:<Instance>");
		drivers.put("com.microsoft.jdbc.sqlserver.SQLServerDriver",
					"jdbc:microsoft:sqlserver://<Hostname>:<Port:1433>;SelectMethod=cursor");
		drivers.put("org.postgresql.Driver",
					"jdbc:postgresql://<Hostname>:<Port:5432>/<Database>");
		drivers.put("ibm.sql.DB2Driver",
					"jdbc:db2:<Hostname>");
		jdbcDrivers = drivers;
	}

	/**
	 * Copies the values from the platform-specific url fields into the main
	 * url.
	 */
    private void updateUrlFromFields() {
        if (updatingFieldsFromUrl) return;
        String template = getTemplateForDriver(dbDriverField.getSelectedItem().toString());
        if (template == null) return;
        try {
            updatingUrlFromFields = true;
            StringBuffer newUrl = new StringBuffer();
            Pattern p = Pattern.compile("<(.*?)>");
            Matcher m = p.matcher(template);
            while (m.find()) {
                String varName = m.group(1);
                if (varName.indexOf(':') != -1) {
                    varName = varName.substring(0, varName.indexOf(':'));
                }
                String varValue = getPlatformSpecificFieldValue(varName);
                m.appendReplacement(newUrl, varValue);
            }
            m.appendTail(newUrl);
            dbUrlField.setText(newUrl.toString());
        } finally {
            updatingUrlFromFields = false;
        }
    }

    /**
     * Parses the main url against the current template (if possible) and fills in the 
     * individual fields with the values it finds.
     */
    private void updateFieldsFromUrl() {
        if (updatingUrlFromFields) return;
        try {
            updatingFieldsFromUrl = true;

            for (int i = 0; i < platformSpecificOptions.getComponentCount(); i++) {
                platformSpecificOptions.getComponent(i).setEnabled(true);
            }
            
            String template = getTemplateForDriver(dbDriverField.getSelectedItem().toString());
            logger.debug("Updating based on template "+template);
            if (template == null) return;
            String reTemplate = template.replaceAll("<.*?>", "(.*)");
            logger.debug("Regex of template is "+reTemplate);
            Pattern p = Pattern.compile(reTemplate);
            Matcher m = p.matcher(dbUrlField.getText());
            if (m.find()) {
                platformSpecificOptions.setEnabled(true);
                for (int g = 1; g <= m.groupCount(); g++) {
                    ((JTextField) platformSpecificOptions.getComponent(2*g-1)).setText(m.group(g));
                }
            } else {
                for (int i = 0; i < platformSpecificOptions.getComponentCount(); i++) {
                    platformSpecificOptions.getComponent(i).setEnabled(false);
                }
            }
        } finally {
            updatingFieldsFromUrl = false;
        }
    }
    
	/**
	 * Retrieves the named platform-specific option by looking it up in the
	 * platformSpecificOptions component. 
     */
    private String getPlatformSpecificFieldValue(String varName) {
        // we're looking for the contents of the JTextField that comes after a JLabel with the same text as varName
        for (int i = 0; i < platformSpecificOptions.getComponentCount(); i++) {
            if (platformSpecificOptions.getComponent(i) instanceof JLabel
                    && ((JLabel) platformSpecificOptions.getComponent(i)).getText().equals(varName)
                    && platformSpecificOptions.getComponentCount() >= i+1) {
                return ((JTextField) platformSpecificOptions.getComponent(i+1)).getText();
            }
        }
        return "";
    }

    // -------------------- ARCHITECT PANEL INTERFACE -----------------------

	/**
	 * Copies the properties displayed in the various fields back into
	 * the current DBConnectionSpec.  You still need to call getDbcs()
	 * and save the connection spec yourself.
	 */
	public void applyChanges() {
		String name = dbNameField.getText();
		dbcs.setName(name);
		dbcs.setDisplayName(name);
		dbcs.setDriverClass(dbDriverField.getSelectedItem().toString());
		dbcs.setUrl(dbUrlField.getText());
		dbcs.setUser(dbUserField.getText());
		dbcs.setPass(new String(dbPassField.getPassword())); // completely defeats the purpose for JPasswordField.getText() being deprecated, but we're saving passwords to the config file so it hardly matters.
	}

	/**
	 * Does nothing right now.
	 */
	public void discardChanges() {
        // nothing to discard
	}

	/**
	 * Sets this DBCSPanel's fields to match those of the given dbcs,
	 * and stores a reference to the given dbcs so it can be updated
	 * when the applyChanges() method is called.
	 */
	public void setDbcs(DBConnectionSpec dbcs) {
		dbNameField.setText(dbcs.getName());
		dbDriverField.removeItemAt(0);
		if (dbcs.getDriverClass() != null) {
			dbDriverField.insertItemAt(dbcs.getDriverClass(), 0);
		} else {
			dbDriverField.insertItemAt("", 0);
		}
		dbDriverField.setSelectedIndex(0);
		dbUrlField.setText(dbcs.getUrl());
		dbUserField.setText(dbcs.getUser());
		dbPassField.setText(dbcs.getPass());
		this.dbcs = dbcs;
	}

	/**
	 * Returns a reference to the current DBConnectionSpec (that is,
	 * the one that will be updated when apply() is called).
	 */
	public DBConnectionSpec getDbcs() {
		return dbcs;
	}

	private class JDBCURLUpdater implements DocumentListener {

        public void insertUpdate(DocumentEvent e) {
            updateUrlFromFields();
        }

        public void removeUpdate(DocumentEvent e) {
            updateUrlFromFields();
        }

        public void changedUpdate(DocumentEvent e) {
            updateUrlFromFields();
        }
	}
	
	private static class PlatformOptionsLayout implements LayoutManager {

	    /** The number of pixels to leave before each label except the first one. */
	    int preLabelGap = 10;

	    /** The number of pixels to leave between every component. */
	    int gap = 5;
	    
	    public void addLayoutComponent(String name, Component comp) {
            // nothing to do
        }
	    
        public void removeLayoutComponent(Component comp) {
            // nothing to do
        }

        public Dimension preferredLayoutSize(Container parent) {
            int height = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);
                height = Math.max(height, c.getPreferredSize().height);
            }
            return new Dimension(parent.getWidth(), height);
        }

        public Dimension minimumLayoutSize(Container parent) {
            int height = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);
                height = Math.max(height, c.getMinimumSize().height);
            }
            return new Dimension(parent.getWidth(), height);
        }

        public void layoutContainer(Container parent) {
           
            // compute total width of all labels
            int labelSize = 0;
            int labelCount = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);
                if (c instanceof JLabel) {
                    if (i > 0) labelSize += preLabelGap;
                    labelSize += c.getPreferredSize().width;
                    labelCount += 1;
                }
            }

            int gapSize = gap * (parent.getComponentCount() - 1);
            
            // compute how wide each non-label component should be (if there are any non-labels)
            int nonLabelWidth = 0;
            if (parent.getComponentCount() != labelCount) {
                nonLabelWidth = (parent.getWidth() - labelSize - gapSize) / (parent.getComponentCount() - labelCount);
            }
            
            // impose a minimum so the non-labels at least show up when we're tight on space
            if (nonLabelWidth < 20) {
                nonLabelWidth = 20;
            }

            // lay out the container
            int x = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);

                if (i > 0) x += gap;
                
                if (c instanceof JLabel) {
                    if (i > 0) x += preLabelGap;
                    c.setBounds(x, 0, c.getPreferredSize().width, parent.getHeight());
                    x += c.getPreferredSize().width;
                } else {
                    c.setBounds(x, 0, nonLabelWidth, parent.getHeight());
                    x += nonLabelWidth;
                }
            }
        }
	}
}