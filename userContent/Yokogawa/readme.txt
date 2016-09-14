Contribute_ImageList Job: Yokogawa example_01

This is an example Yokogawa CellVoyager CV7000 acquisition folder. It contains two metadata files (mlf,mrf) and in a real acquisition would also contain the corresponding images.
Images are not included in this example, but the metadata files are sufficient to create a CellProfiler image list using the job 'Contribute_ImageList'.

Note that there are 3 channels in this acquisition each of which will need to be assigned to a 'biological concept' in the CellProfiler image list.

If it was not for the metadata files you would have to extract corresponding metadata from each image file name. 

Typical file names for Yokogawa images look like this:
CM00619158_A01_T0001F001L01A01Z01C01.tif
CM00619158_A01_T0001F001L01A02Z01C02.tif
CM00619158_A01_T0001F001L01A03Z01C03.tif

with barcodes, wells and other metadata (time points, z-stacks, channels etc.) embedded in the file name.

The Contribute_ImageList Yokogawa metadata parser recognizes and extracts metadata automatically to generate correctly formatted CellProfiler image lists