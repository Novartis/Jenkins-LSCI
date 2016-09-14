Contribute_ImageList Job: Example_01_4_channel
Note: The xdce file must match the name of the acquisition folder.

This is an example InCell acquisition folder. It contains the xdce metadata file and in a real example would also contain the corresponding images.
Images are not included in this example, but the xdce file is sufficient to create a CellProfiler image list using the job 'Contribute_ImageList'.

Note that there are 4 channels in this acquisition, each of which will need to be assigned to a 'biological concept' in the CellProfiler image list.

The channels are:
1. DAPI
2. Cy5
3. dsRed
4. FITC

An example assignment
1. DAPI:Nuclei
2. Cy5:Myosin
3. dsRed:Actin
4. FITC:GFP

If you use the 'assisted' option you can auto-detect the 4 acquisition channels (as defined in the xdce) and assign them to biological concepts. 
Note that the names of channels are case sensitive. If you do not assign all the channels or the provided names mismatch the channel names in the xdce you will get a run time error.
Examine the build console output for more details on what caused the error and how to resolve it! 