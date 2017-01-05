package com.tkurimura.flickabledialog;

public class FlickableDialogListener {

  public static class X_DIRECTION {
    public static int LEFT_TOP = 0;
    public static int RIGHT_TOP = 1;
    public static int RIGHT_BOTTOM = 2;
    public static int LEFT_BOTTOM = 3;
  }

  public interface OnFlickedXDirection {
    /**
     * callback flicking direction in categorization of X area
     *
     * @param xDirection LEFT_TOP,RIGHT_TOP,RIGHT_BOTTOM,LEFT_BOTTOM
     * @version 0.3.0
     */
    void onFlickableDialogFlicked(int xDirection);
  }

  public interface OnCanceled {
    /**
     * callback touched outside or pressed back key.
     *
     * @version 0.4.0
     */
    void onFlickableDialogCanceled();
  }
}
