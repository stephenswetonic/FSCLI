import org.apache.commons.io.FileUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

public class focusStacker {
    String projectName;
    ArrayList<File> colorImages;
    ArrayList<File> colorImagesAligned;
    ArrayList<String> colorImagePaths;
    ArrayList<blurMap> blurMaps;
    String resultPath;
    File result;
    int threads;

    focusStacker(ArrayList<String> paths, String projectName, int threads) {
        this.threads = threads;
        this.colorImagePaths = paths;
        this.projectName = projectName + "/";
        resultPath = this.projectName + "result" + ".jpg";
        result = new File(resultPath);

        //Create files from paths
        colorImages = new ArrayList<>();
        for (String path : paths) {
            colorImages.add(new File(path));
        }

        File projectDir = new File(this.projectName);
        if (projectDir.mkdir()) {
            System.out.println("New Directory Created");
        } else {
            System.out.println("Clearing Directory");
            try {
                FileUtils.cleanDirectory(new File(this.projectName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        colorImagesAligned = new ArrayList<>();
        int i = 0;
        for (String path : paths) {
            File f = new File(this.projectName + "aligned" + i + ".jpg");
            colorImagesAligned.add(f);
            i++;
        }
    }

    public void ECCalignAll() {
        ArrayList<Mat> input = new ArrayList<>();
        for (String path : colorImagePaths) {
            input.add(Imgcodecs.imread(path));
        }

        System.out.println(("Aligning image stack"));
        ArrayList<Mat> newStack = new ArrayList<>();
        newStack.add(input.get(0));

        for(int i = 0; i < input.size() - 1; i++) {
            newStack.add(ECCalignment(newStack.get(i), input.get(i+1)));
        }
        System.out.println("Saving aligned images");
        for (int i = 0; i < newStack.size(); i++) {
            Imgcodecs.imwrite(colorImagesAligned.get(i).getAbsolutePath(), newStack.get(i));
        }
    }

    public Mat ECCalignment(Mat template, Mat input) {
        Mat alignedInput = Mat.zeros(template.size(), template.type());
        Mat templateGray = Mat.zeros(template.size(), CvType.CV_8UC1);
        Mat inputGray = Mat.zeros(template.size(), CvType.CV_8UC1);

        Imgproc.cvtColor(template, templateGray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(input, inputGray, Imgproc.COLOR_BGR2GRAY);

        // Think of aligning a stack of images as a video tracking problem
        int warpMode = Video.MOTION_HOMOGRAPHY;
        Mat warpMatrix = new Mat();


        // Initialize identity matrix
        if (warpMode == Video.MOTION_HOMOGRAPHY) {
            warpMatrix = Mat.eye(3, 3, CvType.CV_32FC1); //3x3 warp matrix
        } else {
            warpMatrix = Mat.eye(2, 3, CvType.CV_32FC1); //2x3 warp matrix
        }

        // Termination criteria
        int maxIterations = 2500; //maximum iterations or elements
        double terminationEps = 1e-4; //desired accuracy

        TermCriteria killCondition = new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, maxIterations, terminationEps);

        //Finds the geometric transformation between the template image and input image
        Video.findTransformECC(templateGray, inputGray, warpMatrix, warpMode, killCondition, templateGray, 1);



        // Warps the input to the template image, thus aligning the two
        if (warpMode != Video.MOTION_HOMOGRAPHY) {
            Imgproc.warpAffine(input, alignedInput, warpMatrix, input.size(), Imgproc.WARP_INVERSE_MAP + Imgproc.INTER_LINEAR);
        } else {
            Imgproc.warpPerspective(input, alignedInput, warpMatrix, input.size(), Imgproc.WARP_INVERSE_MAP + Imgproc.INTER_LINEAR);
        }


        return alignedInput;
    }


    /*
     Initializes array of blurMaps from the color images.
     A project with x number of images will have x blurMap
     Then each blurMap is initialized with image data and
     split into pieces for multiple threads. Then, analyzeSVD is
     called on each blurMap to mask the original image.
    */
    public void initBlurMaps() throws IOException {
        blurMaps = new ArrayList<>();

        int imagesLeft = colorImagesAligned.size();
        int index = 0;
        int toMake = 0;
        int threads = this.threads;

        while (imagesLeft > 0) {
            ArrayList<blurMap> engineThreads = new ArrayList<>();
            toMake = Math.min(imagesLeft, threads);

            // make blurmaps
            for (int i = 0; i < toMake; i++) {
                blurMap bm = new blurMap(colorImagesAligned.get(index), 10, 3, projectName);
                blurMaps.add(bm);
                engineThreads.add(bm);
                index++;
            }
            // start
            // join

            for (blurMap fe : engineThreads) {
                fe.start();
            }
            for (blurMap fe : engineThreads) {
                try {
                    fe.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            imagesLeft -= toMake;
        }

        composeResult();

    }

    public void composeResult() {
        // define result img
        // for every x
        //   for every y
        //     for every image in set
        //       get max blurmap
        //       set pixel to that x,y
        blurMap firstImage = blurMaps.get(0);
        BufferedImage result = new BufferedImage(blurMaps.get(0).originalWidth, blurMaps.get(0).originalHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < firstImage.originalHeight; y++) {
            for (int x = 0; x < firstImage.originalWidth; x++) {

                double minValue = Double.MAX_VALUE;
                BufferedImage maxBF = blurMaps.get(0).originalColorBf;
                for (int i = 0; i < blurMaps.size(); i++) {
                    if (blurMaps.get(i).blurMap[x][y] < minValue && blurMaps.get(i).blurMap[x][y] < 0.75) {
                        minValue = blurMaps.get(i).blurMap[x][y];
                        maxBF = blurMaps.get(i).originalColorBf;
                    } else {
                        maxBF = blurMaps.get(0).originalColorBf;
                    }
                }

                // Set pixel
                result.setRGB(x, y, maxBF.getRGB(x, y));
            }
        }
        try {
            ImageIO.write(result, "jpg", this.result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
