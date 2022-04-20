///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.pdfbox:pdfbox:2.0.25
//DEPS info.picocli:picocli:4.6.3

import static java.lang.System.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "images2pdf", mixinStandardHelpOptions = true, version = "1.0",
         description = "Create PDF from images.")
public class images2pdf implements Callable<Integer> {

    @Parameters(index = "0", description = "Image list file.")
    private String imageListFile;

    @Override
    public Integer call() throws Exception {
        out.println("Create PDF from images");

        Scanner scanner = new Scanner(new File(imageListFile));
        ArrayList<String> imageList = new ArrayList<String>();
        while (scanner.hasNext()) {
            imageList.add(scanner.next());
        }
        scanner.close();

        PDDocument document = new PDDocument();
        for (String imagePath : imageList) {
            imagePath = imagePath.trim();
            if (imagePath.length() > 0) {
                out.println("-- image: " + imagePath);

                InputStream in = new FileInputStream(imagePath);
                BufferedImage bimg = ImageIO.read(in);
                float width = bimg.getWidth();
                float height = bimg.getHeight();

                PDPage page = new PDPage(new PDRectangle(width, height));
                document.addPage(page);

                PDImageXObject pdImage = PDImageXObject.createFromFile(imagePath, document);
                PDPageContentStream contentStream = new PDPageContentStream(document, page);
                contentStream.drawImage(pdImage, 0, 0);
                contentStream.close();
                in.close();
            }
        }

        document.save(imageListFile + ".pdf");
        document.close();

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new images2pdf()).execute(args);
        System.exit(exitCode);
    }

}
