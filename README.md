# Focus Stack CLI

Focus Stacker CLI is a tool for focus stacking photos. The software combines an alignment algorithm with a filtering algorithm, then composites the set of photos into a result with greater depth of field.

For best results, use a tripod with no movement in between shots. If there's too much of a difference between photos, the alignment step won't be able to complete. It can take quite a long time to process (6 minutes or so for a 15MP image).

If you don't specify the number of threads, the software will utilize all available threads.

### Prerequisites:

 - Java

### Usage

 1. Download FocusStackCLI.jar.
 2. Navigate to jar location.
 3. Create a folder with the images you want to stack.
 4. Run `java -jar FocusStackCLI.jar {images directory} {project name} {threads}`

# Examples

## Image Set










