import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Iterator;
import static javax.imageio.ImageIO.read;

public class blurMap extends Thread{
    File original;
    File originalColor;
    File maskedImage;
    BufferedImage originalColorBf;
    int blocksize;
    int svdNum;
    int originalWidth;
    int originalHeight;
    double[][] blurMap;

    public blurMap(File inputColor, int blocksize, int svdNum, String projectName) {
        String filePathG = projectName + Calendar.getInstance().getTimeInMillis() + "g" + ".jpg";
        String filePathM = projectName + Calendar.getInstance().getTimeInMillis() + "m" + ".png";
        originalColor = inputColor;
        original = new File(filePathG);
        try {
            FileUtils.copyFile(originalColor, original);
        } catch (IOException e) {
            e.printStackTrace();
        }
        toGrayscale(original);
        maskedImage = new File(filePathM);
        this.blocksize = blocksize;
        this.svdNum = svdNum;

    }


    public void run() {
        getBlurMap();
        analyzeSVD();
    }

    // Converts image to grayscale
    public void toGrayscale(File in) {
        long startTime = System.currentTimeMillis();
        try {
            ImageInputStream iis = ImageIO.createImageInputStream(in);
            Iterator<ImageReader> iterator = ImageIO.getImageReaders(iis);
            ImageReader reader = iterator.next();
            String imageFormat = reader.getFormatName();

            BufferedImage image = ImageIO.read(iis);
            int width = image.getWidth();
            int height = image.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color color = new Color(image.getRGB(x, y));
                    int red = (int) (color.getRed() * 0.2126);
                    int green = (int) (color.getGreen() * 0.7152);
                    int blue = (int) (color.getBlue() * 0.0722);
                    int sum = red + green + blue;
                    if (sum == 0) {
                        sum += 1;
                    }
                    Color shadeOfGray = new Color(sum, sum, sum);
                    image.setRGB(x, y, shadeOfGray.getRGB());
                }
            }
            ImageIO.write(image, imageFormat, original);

        } catch (IOException e){
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Grayscale took: " + (endTime - startTime) + "ms");
    }

    // Blurriness algorithm designed to work on 1 image
    public void getBlurMap() {
        long startTime = System.currentTimeMillis();
        BufferedImage image = null;
        try {
            image = read(original);
        } catch (IOException e) {
            e.printStackTrace();
        }
        originalWidth = image.getWidth();
        originalHeight = image.getHeight();
        int newImgWidth = originalWidth + (blocksize * 2);
        int newImgHeight = originalHeight + (blocksize * 2);
        double[][] newImg = new double[newImgWidth][newImgHeight];

        System.out.println(this.originalColor);
        try {
            originalColorBf = read(this.originalColor);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Filling newImg with image data and handling edges
        for (int i = 0; i < newImgHeight; i++) {
            for (int j = 0; j < newImgWidth; j++) {
                int p = 0;
                int q = 0;
                if ( i < blocksize) {
                    p = blocksize - i;
                } else if (i > (originalHeight + blocksize - 1)) {
                    p = originalHeight*2-i;
                } else {
                    p = i - blocksize;
                }
                if ( j < blocksize) {
                    q = blocksize - j;
                } else if (j > (originalWidth + blocksize - 1)) {
                    q = originalWidth*2-j;
                } else {
                    q = j - blocksize;
                }
                Color c = new Color(image.getRGB(q, p));
                int grayVal = c.getRed();
                newImg[j][i] = grayVal;
            }
        }
        blurMap = new double[originalWidth][originalHeight];

        double maxSV = 0;
        double minSV = 1;
        for (int i = 0; i < originalHeight; i++) {
            for (int j = 0; j < originalWidth; j++) {
                double[][] block = fillBlock(newImg, i, j);
                //fill block
                SingularValueDecomposition svd = new SingularValueDecomposition(MatrixUtils.createRealMatrix(block));
                double[] s = svd.getSingularValues();
                double topSV = 0;
                double totalSV = 0;
                for (int k = 0; k < svdNum; k++) {
                    topSV += s[k];
                }
                for (double v : s) {
                    totalSV += v;
                }
                double svDegree = topSV / totalSV;
                maxSV = Math.max(maxSV, svDegree);
                minSV = Math.min(minSV, svDegree);
                blurMap[j][i] = svDegree;
            }
        }
        for (int i = 0; i < originalHeight; i++) {
            for (int j = 0; j < originalWidth; j++) {
                blurMap[j][i] = (blurMap[j][i] - minSV) / (maxSV - minSV);
            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Filtering took: " + (endTime - startTime) + "ms");
    }

    // Writes the blur map data to a png image
    // The result will be like a grayscale image with brighter values representing higher sharpness
    // This is a bit unnecessary because the image with suffix 'm' gets created
    public void getMaskedImage(File out) {
        BufferedImage original = null;
        try {
            original = read(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedImage maskedImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < originalHeight-1; y++) {
            for (int x = 0; x < originalWidth-1; x++) {
                //Percentage of 255
                int grayValue = (int) ((1-blurMap[x][y]) * 255);
                if (grayValue < 0) {
                    grayValue = 0;
                }
                if (grayValue > 255) {
                    grayValue = 255;
                }

                Color c = new Color(grayValue, grayValue, grayValue);
                maskedImage.setRGB(x, y, c.getRGB());
            }
        }
        try {
            ImageIO.write(maskedImage, "png", out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void analyzeSVD() {
        try {
            BufferedImage image = read(this.originalColor);
            BufferedImage outputImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < originalHeight; y++) {
                for (int x = 0; x < originalWidth; x++) {
                    Color c = new Color(image.getRGB(x, y));
                    int alpha = (int) ((1 - blurMap[x][y]) * 255);

                    // Not sure why alpha could go negative, but it can
                    if (alpha < 0) {
                        alpha = 0;
                    }
                    Color newC = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
                    outputImage.setRGB(x, y, newC.getRGB());
                }
            }
            ImageIO.write(outputImage, "png", maskedImage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Fills a matrix with image data
    public double[][] fillBlock(double[][] imageArray, int y, int x) {
        double[][] block = new double[blocksize*2][blocksize*2];
        for (int i = 0; i < blocksize*2; i++) {
            for (int j = 0; j < blocksize*2; j++) {
                block[j][i] = imageArray[j+x][i+y];
            }
        }
        return block;
    }
}


