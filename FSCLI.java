import nu.pattern.OpenCV;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

public class FSCLI {

    // arg1: directory with images (png,jpg)
    // arg2: project name
    public static void main(String[] args) {
        OpenCV.loadShared();
        String directory;
        int threads;
        File dir = null;
        if (args.length > 0) {
            directory = args[0];
            dir = new File(directory);
        } else {
            System.out.println("No directory specified");
            System.exit(1);
        }
        ArrayList<String> images = new ArrayList<>();
        File[] files = null;
        if (dir.isDirectory()) {
            files = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".png") ||
                            name.toLowerCase().endsWith(".jpg") ||
                            name.toLowerCase().endsWith(".jpeg");
                }
            });

        } else {
            System.out.println("Not a directory");
            System.exit(1);
        }
        if (files != null) {
            if (files.length > 0) {
                for (File f : files) {
                    images.add(f.getAbsolutePath());
                }
            } else {
                System.out.println("No files");
                System.exit(1);
            }
        } else {
            System.out.println("File list null");
            System.exit(1);
        }

        if (args.length < 2) {
            System.out.println("Please provide a project name");
            System.exit(1);
        }

        if (args.length < 3) {
            threads = Runtime.getRuntime().availableProcessors();
        } else {
            threads = Integer.parseInt(args[2]);
        }

        focusStacker fs = new focusStacker(images, args[1], threads);
        fs.ECCalignAll();
        try {
            fs.initBlurMaps();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
