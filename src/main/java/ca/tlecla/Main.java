package main.java.ca.tlecla;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Stack;
import javax.imageio.ImageIO;

/**
 * The type Main.
 *
 * @author Tristan Leclair-Vani
 */
public class Main {

  /**
   * Input image.
   */
  public static BufferedImage img;

  /**
   * Parameters and their default values.
   */
  public static String imagename = "test3.jpg"; // name of the input image
  /**
   * Number of threads to use.
   */
  public static int threads = 8;
  /**
   * Threshold to detect luminosity change.
   */
  public static double threshold = 0.1;
  /**
   * Number of same luminosities in a sequence required.
   */
  public static int n = 8;

  /**
   * Luminosity double.
   *
   * @param p the pixel
   * @return luminosity of the RGB pixel value
   */
  public static double luminosity(int p) {
    // just extract the RGB values (masking off the alpha value) and call the other helper
    return luminosity(((p >> 16) & 0xff), ((p >> 8) & 0xff), ((p) & 0xff));
  }

  /**
   * Luminosity double.
   * <p>
   * This follows a common luminosity calculation
   * </p>
   *
   * @param r the red
   * @param g the green
   * @param b the blue
   * @return luminosity given the 3 separated RGB pixel values
   */
  public static double luminosity(int r, int g, int b) {
    double newR = 0.21 * linearize(((double) r) / 255.0);
    double newG = 0.72 * linearize(((double) g) / 255.0);
    double newB = 0.07 * linearize(((double) b) / 255.0);
    return newR + newG + newB;
  }

  /**
   * Linearize an R, G, or B value normalized to 0.0-1.0.
   *
   * @param d the double
   * @return the linearized value
   */
  static double linearize(double d) {
    return (d <= 0.04045) ? d / 12.92 : Math.pow((d + 0.055) / 1.055, 2.4);
  }

  /**
   * Print out command-line parameter help and exit.
   *
   * @param s the s
   */
  public static void help(String s) {
    System.out.println(
        "Could not parse argument \"" + s + "\".  Please use only the following arguments:");
    System.out.println(" -i imagename (string; current=\"" + imagename + "\")");
    System.out.println(
        " -d luminosity threshold (floating point value 0.0-1.0; current=\"" + threshold + "\")");
    System.out.println(
        " -n different luminosities threshold (integer value 0-16; current=\"" + n + "\")");
    System.out.println(" -t threads (integer value >=1; current=\"" + threads + "\")");
    System.exit(1);
  }

  /**
   * Process command-line options.
   *
   * @param args the args
   */
  public static void opts(String[] args) {
    int i = 0;

    try {
      for (; i < args.length; i++) {

        if (i == args.length - 1) {
          help(args[i]);
        }

        switch (args[i]) {
          case "-i" -> imagename = args[i + 1];
          case "-d" -> threshold = Double.parseDouble(args[i + 1]);
          case "-n" -> n = Integer.parseInt(args[i + 1]);
          case "-t" -> threads = Integer.parseInt(args[i + 1]);
          default -> help(args[i]);
        }
        // an extra increment since our options consist of 2 pieces
        i++;
      }
    } catch (Exception e) {
      System.err.println(e);
      help(args[i]);
    }
  }

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws IOException in case the image loading/storing fails
   */
  public static void main(String[] args) throws IOException {
    System.out.println(imagename);
    // process options
    opts(args);

    System.out.println(imagename);
    // read in the image
    img = ImageIO.read(new File(imagename));
    int width = img.getWidth();
    int height = img.getHeight();

    // copy the image for the annotated output.
    // this is not the most efficient way to do this, but it shows how to access a single pixel
    BufferedImage outputimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int i = 0; i < width; i++) {
      for (int j = 0; j < height; j++) {
        outputimage.setRGB(i, j, img.getRGB(i, j));
      }
    }

    long startApp = System.currentTimeMillis();

    boolean[][] pixels = new boolean[width][height];

    long startCornerTime = System.currentTimeMillis();
    ReaderThread[] threadInstances = new ReaderThread[threads];
    Stack<Point2D> point2dStack = new Stack<>();
    for (int i = 0; i < threads; ++i) {
      threadInstances[i] = new ReaderThread(3, width - 3, 3, height - 3, pixels, outputimage);
      threadInstances[i].start();
    }

    int totalCorners = 0;

    for (int i = 0; i < threads; ++i) {
      try {
        threadInstances[i].join();
        point2dStack.addAll(threadInstances[i].getToDraw());
        totalCorners += threadInstances[i].getTotalCorners();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    long finishCornerTime = System.currentTimeMillis();

    long startTimeDraw = System.currentTimeMillis();
    DrawThread[] drawThreads = new DrawThread[threads];
    for (int i = 0; i < threads; ++i) {
      drawThreads[i] = new DrawThread(outputimage, point2dStack);
      drawThreads[i].start();
    }
    for (int i = 0; i < threads; ++i) {
      try {
        drawThreads[i].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    long finishTimeDraw = System.currentTimeMillis();


    long totalTimeForCorners = finishCornerTime - startCornerTime;
    long totalTimeDraw = finishTimeDraw - startTimeDraw;


    System.out.println("Total time: " + (System.currentTimeMillis() - startApp));
    System.out.println("Total corners: " + totalCorners);
    System.out.println("Time for corner detection: " + totalTimeForCorners);
    System.out.println("Time for draw: " + totalTimeDraw);


    // Write out the image
    File outputfile = new File("outputimage3.png");
    ImageIO.write(outputimage, "png", outputfile);
  }

  /**
   * Count consecutive true values in a circular array.
   *
   * @param bools array of booleans
   * @return nb of consecutive true values
   */
  public static int countConsecutives(int[] bools) {
    int startInd = 0;
    int firstCount = 0; //nb of consecutives starting from the front
    int len = bools.length;
    Boolean pos = null; // keep track of last value

    //starts looping through array counting consecutive trues at the beginning
    // (to attach to the end if applicable)
    //will stop once it reaches either the end (all bools are true)
    //or when it reaches a false
    while (startInd < len && bools[startInd] != 0) {
      if (pos == null) {
        pos = bools[startInd] > 0;
      } else if (pos != bools[startInd] > 0) {
        break;
      }
      firstCount++;
      startInd++;
    }

    //start from the end
    int secondCount = 0; //nb of consecutives starting from the back
    int endOfArr = len - 1;
    pos = null;
    while (endOfArr >= 0 && bools[endOfArr] != 0) {
      if (pos == null) {
        pos = bools[endOfArr] > 0;
      } else if (pos != bools[endOfArr] > 0) {
        break;
      }
      secondCount++;
      endOfArr--;
    }

    //if startInd and endOfArr crossed, then the entire array is true
    if (startInd > endOfArr) {
      return len;
    }

    //now do the same but for any indexes that were missed in the first two passes
    pos = null;
    int thirdCounter = 0; //counter used for cons
    int finalRes = 0; //final max arr
    for (int i = startInd; i <= endOfArr; ++i) {
      if (bools[i] != 0) {
        if (pos == null) { //last chain was broken or we just started
          pos = bools[i] > 0;
          thirdCounter++;
          finalRes = Math.max(finalRes, thirdCounter);
        } else if (pos != bools[endOfArr] > 0) { //new val wasn't the same as old chain
          pos = bools[endOfArr] > 0;
          thirdCounter = 1;
          finalRes = Math.max(finalRes, thirdCounter);
        } else { //chain continues
          thirdCounter++;
          finalRes = Math.max(finalRes, thirdCounter);
        }
      } else { //no hit
        pos = null;
        thirdCounter = 0;
      }
    }
    return Math.max(finalRes, firstCount + secondCount);
  }

  /**
   * Create neighbours int [ ] [ ].
   *
   * @param x coordinate of the pixel
   * @param y coordinate of the pixel
   * @return a 2D array containing all the neighbours of X
   * @pre assume no edge spill (i.e. 3<=x<=width-3  3<=y<=height-3)
   */
  public static int[][] createNeighbours(int x, int y) {
    return new int[][] {{x - 3, y - 1}, {x - 3, y}, {x - 3, y + 1}, {x - 2, y + 2}, {x - 1, y + 3},
        {x, y + 3}, {x + 1, y + 3}, {x + 2, y + 2}, {x + 3, y + 1}, {x + 3, y}, {x + 3, y - 1},
        {x + 2, y - 2}, {x + 1, y - 3}, {x, y - 3}, {x - 1, y - 3}, {x - 2, y - 2}
    };
  }

  /**
   * The type Reader thread.
   */
  public static class ReaderThread extends Thread {
    private final int endX;
    private final int endY;
    private final int startX;
    private final int startY;
    private final boolean[][] pixels;
    private final BufferedImage outputimage;
    private int totalCorners;
    private long totalTimeForCorners;
    private long startTimeCorner;
    private long totalTimeDraw;
    private Stack<Point2D.Float> toDraw;

    /**
     * Gets to draw.
     *
     * @return the to draw
     */
    public Stack<Point2D.Float> getToDraw() {
      if (this.isAlive()) {
        throw new RuntimeException("Thread must die before time is accessed");
      }
      return toDraw;
    }

    /**
     * Gets total time draw.
     *
     * @return the total time draw
     */
    public long getTotalTimeDraw() {
      if (this.isAlive()) {
        throw new RuntimeException("Thread must die before time is accessed");
      }
      return totalTimeDraw;
    }

    /**
     * Gets total time for corners.
     *
     * @return the total time for corners
     */
    public long getTotalTimeForCorners() {
      if (this.isAlive()) {
        throw new RuntimeException("Thread must die before time is accessed");
      }
      return totalTimeForCorners;
    }

    /**
     * Gets total corners.
     *
     * @return the total corners
     */
    public int getTotalCorners() {
      if (this.isAlive()) {
        throw new RuntimeException("Thread must die before time is accessed");
      }
      return totalCorners;
    }

    /**
     * Instantiates a new Reader thread.
     *
     * @param startX the start x
     * @param endX   the end x
     * @param startY the start y
     * @param endY   the end y
     * @param pixels the pixels
     * @param output the output
     * @pre assume no edge spill (i.e. 3<=startX,endX<=image width-3 3<=startY,endY<=image height-3)
     */
    public ReaderThread(int startX, int endX, int startY, int endY, boolean[][] pixels,
                        BufferedImage output) {
      this.endX = endX;
      this.endY = endY;
      this.startX = startX;
      this.startY = startY;
      this.pixels = pixels;
      this.outputimage = output;
      totalCorners = 0;
      toDraw = new Stack<>();
    }

    @Override
    public void run() {
      //go through pixels
      startTimeCorner = System.currentTimeMillis();
      for (int i = startX; i < endX; ++i) {
        for (int j = startY; j < endY; ++j) {

          if (checkPixel(i, j)) { //if pixel has already been modified, skip it
            continue;
          }

          double initLumin = luminosity(img.getRGB(i, j));

          //find all neighbours of pixel
          int[][] neighbours = createNeighbours(i, j);


          //count cons
          if (pixelNeighboursConsecutive(initLumin, neighbours)) {

            for (int[] drawPixel : neighbours) {
              toDraw.push(new Point2D.Float(drawPixel[0], drawPixel[1]));
            }

            ++totalCorners;


            //Original code for drawing
            // with much better run time than what I have right now
            // just can't get time counting to work with this unfortunately

            /*
            for (int[] drawPixel : neighbours) {
              outputimage.setRGB(drawPixel[0], drawPixel[1], Color.RED.getRGB());
            }
            */
          }
        }
      }

    }

    /**
     * Check if pixel has enough consecutive variation in luminosity pixels to be marked as corner.
     *
     * @param initLumin  luminosity of pixel
     * @param neighbours neighbours of pixel
     * @return true if the pixel's neighbours need to be drawn
     */
    private boolean pixelNeighboursConsecutive(double initLumin, int[][] neighbours) {

      int[] isLumin = new int[16];
      int count = 0;
      for (int[] pixel : neighbours) {
        //get original color

        int x = pixel[0];
        int y = pixel[1];
        img.getRGB(x, y);
        double neighbourLumin = luminosity(img.getRGB(x, y));


        //compare luminosities
        double diff = initLumin - neighbourLumin;
        if (Math.abs(diff) > threshold) {
          if (diff < 0) {
            isLumin[count] = -1;
          } else {
            isLumin[count] = 1;
          }
        }
        ++count;
      }
      return countConsecutives(isLumin) > n;
    }

    /**
     * Check if pixel was/is being accessed or modified and mark it as accessed.
     *
     * @param i x coordinate of pixel
     * @param j y coordinate of pixel
     * @return true if the pixel has been accessed
     */
    private boolean checkPixel(int i, int j) {
      synchronized (pixels) {
        boolean res = pixels[i][j];
        pixels[i][j] = true;
        return res;
      }
    }
  }

  /**
   * The type Draw thread.
   */
  public static class DrawThread extends Thread {

    private final BufferedImage outputimage;
    private final Stack<Point2D> toDraw;

    /**
     * Instantiates a new Draw thread.
     *
     * @param outputimage the outputimage
     * @param toDraw      the to draw
     */
    public DrawThread(BufferedImage outputimage, Stack<Point2D> toDraw) {
      this.outputimage = outputimage;
      this.toDraw = toDraw;
    }

    @Override
    public void run() {
      Point2D.Float coords;
      while (!toDraw.isEmpty()) {
        synchronized (toDraw) {
          if (!toDraw.isEmpty()) {
            coords = (Point2D.Float) toDraw.pop();
            outputimage.setRGB((int) coords.getX(), (int) coords.getY(), Color.RED.getRGB());
          }
        }
      }
    }
  }
}
