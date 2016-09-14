Example CellProfiler Pipelines

This folder contains example CellProfiler pipelines that import images using the LoadData module.

Note: All pipelines used in Jenkins-LSCI must use the LoadData module to load and associate image files with metadata.

Despite the default use of the CellProfiler 'Input Modules' for pipelines that run on users desktops, these modules are not recommended for use when CellProfiler runs in batch mode.
Instead, 'Input Modules' are replaced by the 'LoadData' module. This module is fundamental for batch processing by CellProfiler. Read the module detail instructions here:http://d1zymp9ayga15t.cloudfront.net/CPmanual/LoadData.html

The LoadData module uses as input image lists. Image lists contain the paths and file names for images to load, as well as text or numerical metadata to be associated with these images.

If a pipeline does not start with a LoadData module, it must be manually adapted to do so and use an image list as image input.

Use the Contribute_Pipeline Jenkins-LSCI job to upload such LoadData pipelines to the server for re-use and sharing.

Additional example pipelines can be found here: http://cellprofiler.org/examples.html
Note that these may need to be adapted to use the LoadData module


