/*
 * Copyright (C) 2012 Mikel Artetxe
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
package org.apertium;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Highlighter.HighlightPainter;

/**
 *
 * @author Mikel Artetxe
 */
public class ApertiumGUI extends javax.swing.JFrame {
  private static final boolean hideMarks = true;
  private static final boolean highlightMarkedWords = true;

  public static void prepare() throws Exception {
    Translator.setParallelProcessingEnabled(false);
    Translator.setCacheEnabled(true);
    Translator.setDisplayMarks(true);
    Translator.setJarAsBase();
    if (Translator.getAvailableModes().length == 0)
      throw new Exception("The JAR doesn't include any mode!");
  }
  private HashMap<String, String> titleToMode = new HashMap<String, String>();

  /** Creates new form ApertiumGUI. You must invoce prepare() to check for modes in JAR file first */
  public ApertiumGUI() throws Exception {
    initComponents();
    setLocationRelativeTo(null);

    for (String mode : Translator.getAvailableModes())
      titleToMode.put(Translator.getTitle(mode), mode);
    Object titles[] = titleToMode.keySet().toArray();
    Arrays.sort(titles);
    modesComboBox.setModel(new DefaultComboBoxModel(titles));

    if (modesComboBox.getItemCount() == 0)
      throw new Exception("The JAR doesn't include any mode!");

    modesComboBox.setSelectedIndex(0);

    inputTextArea.requestFocusInWindow();
    inputTextArea.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      public void changedUpdate(DocumentEvent e) {
        update();
      }
    });

    // We create a popup menu for the input text area
    JPopupMenu popup = new JPopupMenu();
    JMenuItem item = new JMenuItem("Copy");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        inputTextArea.copy();
      }
    });
    popup.add(item);
    item = new JMenuItem("Cut");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        inputTextArea.cut();
      }
    });
    popup.add(item);
    item = new JMenuItem("Paste");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        inputTextArea.paste();
      }
    });
    popup.add(item);
    popup.addSeparator();
    item = new JMenuItem("Select all");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        inputTextArea.selectAll();
      }
    });
    popup.add(item);
    inputTextArea.setComponentPopupMenu(popup);

    // We create a popup menu for the output text area
    popup = new JPopupMenu();
    item = new JMenuItem("Copy");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        outputTextArea.copy();
      }
    });
    popup.add(item);
    popup.addSeparator();
    item = new JMenuItem("Select all");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        outputTextArea.selectAll();
      }
    });
    popup.add(item);
    outputTextArea.setComponentPopupMenu(popup);
  }
  private static final Pattern replacePattern = Pattern.compile("\\B(\\*|#|@)\\b");
  private static final Pattern highlightPattern = Pattern.compile("\\B(\\*|#|@)(\\p{L}||\\p{N})*\\b");
  private static final HighlightPainter redPainter = new DefaultHighlightPainter(Color.RED);
  private static final HighlightPainter orangePainter = new DefaultHighlightPainter(Color.ORANGE);
  private boolean textChanged, translating;

  private void update() {
    if (modesComboBox.getSelectedIndex() == -1)
      return;
    textChanged = true;
    if (translating)
      return;
    translating = true;
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (textChanged) {
          textChanged = false;
          try {
            final String translation = Translator.translate(inputTextArea.getText());
            if (displayMarksCheckBox.isSelected() && highlightMarkedWords) {
              int offset = 0;
              outputTextArea.setText(hideMarks ? replacePattern.matcher(translation).replaceAll("") : translation);
              Matcher matcher = highlightPattern.matcher(translation);
              while (matcher.find())
                outputTextArea.getHighlighter().addHighlight(
                    matcher.start() + (hideMarks ? offset-- : offset), matcher.end() + offset,
                    translation.charAt(matcher.start()) == '*' ? redPainter : orangePainter);
            } else
              outputTextArea.setText(translation);
          } catch (Exception ex) {
            Logger.getLogger(ApertiumGUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(ApertiumGUI.this, ex, "Error", JOptionPane.ERROR_MESSAGE);
          }
        }
        translating = false;
      }
    }).start();
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        modesComboBox = new javax.swing.JComboBox();
        displayMarksCheckBox = new javax.swing.JCheckBox();
        inputScrollPane = new javax.swing.JScrollPane();
        inputTextArea = new javax.swing.JTextArea();
        outputScrollPane = new javax.swing.JScrollPane();
        outputTextArea = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Apertium");

        modesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modesComboBoxActionPerformed(evt);
            }
        });

        displayMarksCheckBox.setSelected(true);
        displayMarksCheckBox.setText("Mark unknown words");
        displayMarksCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayMarksCheckBoxActionPerformed(evt);
            }
        });

        inputTextArea.setColumns(20);
        inputTextArea.setLineWrap(true);
        inputTextArea.setRows(5);
        inputTextArea.setWrapStyleWord(true);
        inputScrollPane.setViewportView(inputTextArea);

        outputTextArea.setColumns(20);
        outputTextArea.setEditable(false);
        outputTextArea.setLineWrap(true);
        outputTextArea.setRows(5);
        outputTextArea.setWrapStyleWord(true);
        outputScrollPane.setViewportView(outputTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(outputScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 598, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(modesComboBox, 0, 421, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(displayMarksCheckBox))
                    .addComponent(inputScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 598, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(modesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(displayMarksCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(inputScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(outputScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 178, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void modesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modesComboBoxActionPerformed
      try {
        Translator.setMode(titleToMode.get(modesComboBox.getSelectedItem()));
        update();
      } catch (Exception ex) {
        Logger.getLogger(ApertiumGUI.class.getName()).log(Level.SEVERE, null, ex);
      }
    }//GEN-LAST:event_modesComboBoxActionPerformed

    private void displayMarksCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayMarksCheckBoxActionPerformed
      Translator.setDisplayMarks(displayMarksCheckBox.isSelected());
      update();
    }//GEN-LAST:event_displayMarksCheckBoxActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox displayMarksCheckBox;
    private javax.swing.JScrollPane inputScrollPane;
    private javax.swing.JTextArea inputTextArea;
    private javax.swing.JComboBox modesComboBox;
    private javax.swing.JScrollPane outputScrollPane;
    private javax.swing.JTextArea outputTextArea;
    // End of variables declaration//GEN-END:variables
}
