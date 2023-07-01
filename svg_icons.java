///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4
//DEPS org.apache.xmlgraphics:batik-transcoder:1.16
//DEPS org.apache.xmlgraphics:batik-codec:1.16

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import java.io.*;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import java.nio.file.Paths;

@Command(name = "svg_icons", mixinStandardHelpOptions = true, version = "2023-06-17", 
         description = "Create set of png icons from svg file")
class svg_icons implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file.")
    private String inputFile;

    int[] sizes = { 512, 384, 192, 152, 144, 128, 96, 72 };

    @Override
    public Integer call() throws Exception {

        // Read the input SVG document into Transcoder Input
        String svg_URI_input = Paths.get(inputFile).toUri().toURL().toString();
        TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);

        for (int size : sizes) {
            convert(input_svg_image, size);
        }

        return 0;
    }

    void convert(TranscoderInput input_svg_image, int size) throws IOException, TranscoderException {
        // Define OutputStream to PNG Image and attach to TranscoderOutput
        String outName = "icon-" + size + "x" + size + ".png";
        OutputStream png_ostream = new FileOutputStream(outName);
        TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);

        // Create PNGTranscoder and define hints if required
        PNGTranscoder my_converter = new PNGTranscoder();
        my_converter.addTranscodingHint(PNGTranscoder.KEY_WIDTH, Float.valueOf(size));

        // Convert and Write output
        my_converter.transcode(input_svg_image, output_png_image);

        // Close / flush Output Stream
        png_ostream.flush();
        png_ostream.close();
        System.out.println("Icon created: " + outName);
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new svg_icons()).execute(args);
        System.exit(exitCode);
    }

}
