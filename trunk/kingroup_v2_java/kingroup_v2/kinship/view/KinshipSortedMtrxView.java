package kingroup_v2.kinship.view;
import kingroup_v2.KinGroupV2MainUI;
import kingroup_v2.KinGroupV2Project;
import kingroup_v2.Kingroup;
import kingroup_v2.KingroupFrame;
import kingroup_v2.kinship.Kinship;
import kingroup_v2.kinship.KinshipFileFormat;
import kingroup_v2.pop.sample.sys.SysPopMtrxI;
import kingroup_v2.pop.sample.usr.UsrPopView;
import kingroup_v2.ucm.save_results.UCSaveResultsFileUI;
import pattern.mvc.MVCTableView;
import tsvlib.project.ProjectLogger;

import javax.iox.TextFile;
import javax.swing.*;
import javax.swingx.ProgressWnd;
import javax.swingx.tablex.JTableFactory;
import javax.swingx.tablex.ReadOnlyTableModel;
import javax.utilx.arrays.StrMtrx;
import javax.utilx.arrays.StrVec;
import javax.utilx.arrays.vec.Vec;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Copyright KinGroup Team.
 * User: jc138691, Date: 27/03/2006, Time: 16:52:24
 */
public class KinshipSortedMtrxView  extends MVCTableView {
  private static ProjectLogger log = ProjectLogger.getLogger(KinshipSortedMtrxView.class.getName());
  protected SysPopMtrxI mtrx;
  protected Kinship kinship;
  public final static int N_COLS = 5;
  public final static int N_EXTRA_ROWS = 2;
  private int MAX_N_ROWS = 1000;
  private static final int WRITE_TO_FILE_STEP = 1;
  private static final int WRITE_TO_JTABLE_STEP = 2;
  private static final int N_STEPS = 3;

  public KinshipSortedMtrxView(SysPopMtrxI m, Kinship kinship, String tag) {
//    log.setLevel(Level.OFF);
    this.mtrx = m;
    this.kinship = kinship;
    JTable t = makeTableView(m, tag);
    assemble(t);
  }
  public KinshipSortedMtrxView() {
  }
  private class ThisSortItem {
    public int r;
    public int c;
    public double d;
    public ThisSortItem(int r, int c, double d) {
      this.r = r;
      this.c = c;
      this.d = d;
    }
  }
  protected JTable makeTableView(SysPopMtrxI mtrx, String tag) {
    ProgressWnd progress = new ProgressWnd(KingroupFrame.getInstance(), "Loading matrix view");
    UsrPopView popView = KinGroupV2MainUI.getInstance().getUsrPopView();

    int n = mtrx.size();
    ArrayList<ThisSortItem> sorted = new ArrayList<ThisSortItem>();
    double[] arr = new double[mtrx.getStorageSize()];
    int count = 0;
    for (int r = 0; r < n; r++) {
      int maxC = r - 1;
      if (kinship.getDisplaySortedById())
        maxC = n - 1;
      for (int c = 0; c <= maxC; c++) {
        if (r == c)
          continue;
        if (c < r)
          arr[count++] = mtrx.get(r, c);
        sorted.add(new ThisSortItem(r, c, mtrx.get(r, c)));
      }
    }
    double avr = Vec.sum(arr);
    avr /= arr.length;

    Comparator<ThisSortItem> comp = new Comparator<ThisSortItem>() {
      public int compare(ThisSortItem o1, ThisSortItem o2)       {
        if (kinship.getDisplaySortedById() && o1.r != o2.r)
          return o1.r - o2.r;

        if (kinship.getDisplayAscending())
          return Double.compare(o1.d,  o2.d);
        else
          return -Double.compare(o1.d,  o2.d);
      }     };
    Collections.sort(sorted, comp);

    int nRow = sorted.size() + N_EXTRA_ROWS;
    int nCol = getNCol();

    File file = null;
    TextFile text = null;
    String DELIM = null;
    if (kinship.getSaveToFile()) {
      new UCSaveResultsFileUI().run();
      Kingroup bean = KinGroupV2Project.getInstance();
      KinshipFileFormat format = bean.getKinshipFileFormat();
      DELIM = format.getUserColumnDelim();
      String name = bean.getLastSavedFileName();
      file = bean.makeFile(name);
      text = new TextFile();
      text.setFileName(file.getName());
    }

    if (nRow > MAX_N_ROWS) {
      KinGroupV2MainUI.getInstance().setStatus(" !!!NOTE!!! Displayed only "+MAX_N_ROWS+" out of "+nRow+" rows");
//      JOptionPane.showMessageDialog(null, "nRow=" + nRow);
      nRow = MAX_N_ROWS +  + N_EXTRA_ROWS;
    }

    String[][] rowData = new String[nRow][nCol];
    StrMtrx.set(rowData, JTableFactory.EMPTY);
    String[] colNames = new String[nCol];
    StrVec.set(colNames, JTableFactory.EMPTY);
    if (mtrx.getName() != null && mtrx.getName().length() > 0)
      rowData[0][0] = mtrx.getName();

    colNames[0] = "ID";
    colNames[1] = "Group";
    colNames[2] = "PairID";
    colNames[3] = "PairGroup";
    colNames[4] = tag;
    if (mtrx.getName() != null && mtrx.getName().length() > 0)
      rowData[0][0] = "group: " + mtrx.getName();
    else
      rowData[0][0] = "whole pop";

    loadMeanValue(rowData, avr);
//    rowData[0][4] = tag;

    for (int c = 0; c < nCol; c++) {
      rowData[N_EXTRA_ROWS-1][c] = colNames[c];
    }

    for (int r = 0; r < sorted.size(); r++) {
      ThisSortItem item = sorted.get(r);
      if (progress != null
        && progress.isCanceled(r, 0, Math.min(sorted.size(), MAX_N_ROWS))) {
        return new JTable();
      }

      if (r < MAX_N_ROWS) {
        rowData[r+N_EXTRA_ROWS][0] = popView.getId(mtrx.getId(item.r));
        rowData[r+N_EXTRA_ROWS][1] = popView.getGroupId(mtrx.getId(item.r));
        rowData[r+N_EXTRA_ROWS][2] = popView.getId(mtrx.getId(item.c));
        rowData[r+N_EXTRA_ROWS][3] = popView.getGroupId(mtrx.getId(item.c));
        rowData[r+N_EXTRA_ROWS][4] = loadValue(item.d);
//      loadMeanCol(rowData, r, item.d - avr);
      }
      if (text != null) {
        String line = popView.getId(mtrx.getId(item.r)) + DELIM
          + popView.getGroupId(mtrx.getId(item.r)) + DELIM
          + popView.getId(mtrx.getId(item.c)) + DELIM
          + popView.getGroupId(mtrx.getId(item.c)) + DELIM
          + loadValue(item.d);
        text.addLine(line);
      }
    }

    if (progress != null
      && progress.isCanceled(WRITE_TO_FILE_STEP, 0, N_STEPS)) {
      return new JTable();
    }

    if (text != null  && file != null) {
      text.write(file, KingroupFrame.getInstance());
    }

    if (progress != null
      && progress.isCanceled(WRITE_TO_JTABLE_STEP, 0, N_STEPS)) {
      return new JTable();
    }

    JTable res = new JTable(new ReadOnlyTableModel(rowData, colNames));

    if (progress != null)
      progress.close();
    return res;
  }

  protected void loadMeanValue(String[][] rowData, double avr)
  {
    rowData[0][4] = "mean=" + kinship.format(avr);
  }

  protected String loadValue(double d) {
    return kinship.format(d);
  }

  protected int getNCol() {return N_COLS;}
}

