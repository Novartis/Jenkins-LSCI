#Jenkins-LSCI (Life Science Continuous Integration)
##What can Jenkins-LSCI do?
Jenkins-LSCI enables research scientists to build workflows and data pipelines on the same robust framework and plugin ecosystem as [Jenkins-CI](https://jenkins.io/), the widely used continuous integration server that supports building, deploying and automating any software project. 

##High Content Image Analysis with Jenkins-LSCI and CellProfiler
To demonstrate the utility of Jenkins-LSCI as an integration platform for life-sciences research applications, we provide a set of Jenkins-LSCI jobs that can enhance the usability of [CellProfiler](http://cellprofiler.org) in a  high performance workflow for high content screening image analysis. In addition, these jobs can form the basis for managing and sharing imaging pipelines and data in ways that can enhance scientific collaboration and reproducibility.

These related Jenkins-LSCI jobs enable users to:

1. Create a library of re-usable, annotated CellProfiler pipelines. 
2. Create a library of re-usable, annotated  CellProfiler image lists
3. Prepare task arrays for CellProfiler high performance image analysis (CellProfiler batch mode) on a Linux cluster

## What are the advantages?
-   Image analysis pipelines, image lists, and generated measurements are all managed by the Jenkins-LSCI server and can be reviewed, shared and reused in multiple ways thus building a **collaborative foundation for reproducible research**.

-   CellProfiler can be parallelized to execute on a **high performance** compute cluster.
-   High performance image analysis becomes **accesible to laboratory scientists**.

-   All functionality is **web-accessible**. Users do not need to install CellProfiler locally, or run CellProfiler with the potentially limited compute resources of their local workstation/laptop. 
-  This basic set of jobs can be **easily extended**  with jobs for data and imaging utilities as the computational needs of the laboratory demand.

##The Jenkins-LSCI-CellProfiler Jobs

### OS\_CONTRIBUTE\_PIPELINE

The ‘OS\_CONTRIBUTE\_PIPELINE’ job allows users to annotate and upload a CellProfiler pipeline to the Jenkins server. The build generates a pipeline summary report combining user and internal pipeline annotations. The pipeline is stored on the Jenkins server and can be used by CellProfiler running on a remote cluster or workstation, or on the user’s desktop.

### OS\_CONTRIBUTE\_IMAGELIST

The ‘OS\_CONTRIBUTE\_IMAGELIST’ job facilitates the creation and management of correctly formatted image lists that can be used for processing large number of images through a CellProfiler pipeline. Image processing is highly dependent on image acquisition and experimental metadata (wavelengths, plates, wells, fields, time-points, Z-stacks etc.) We support the direct parsing of metadata generated during the image acquisition phase from the **InCell** and **Yokogawa** scientific imagers.


In the absence of instrument metadata, image lists can be created using the CellProfiler desktop client. These image lists can then be easily adapted using the ‘OS\_CONTRIBUTE\_IMAGELIST’ job to formats compatible with high performance cluster analysis.

### OS\_CELLPROFILER\_BATCH

The ‘OS\_CELLPROFILER\_BATCH’ job allows users to generate correctly formatted CellProfiler task array scripts that can be submitted to a Linux cluster for parallel processing. The job builds take as input a reference to an ‘OS\_CONTRIBUTE\_PIPELINE’ build and a reference to an ‘OS\_CONTRIBUTE\_IMAGELIST’ build. Theses builds define the two inputs for a typical CellProfiler run, the image processing pipeline and the image list.

The task array script is currently formatted for the [UNIVA grid engine](http://www.univa.com/products/) scheduler and it should require little effort to customize for other cluster schedulers. However, due to the non-standardized, local, Linux cluster environment and installed software dependencies, the task array submission process is left up to the user. In addition, CellProfiler and its library dependencies must be installed and correctly working on the destination Linux cluster.

### OS\_CELLPROFILER\_JCLUSTBATCH
The ‘OS\_CELLPROFILER\_JCLUSTBATCH’ job is an example CellProfiler on cluster job that includes the steps for, creating task arrays, submitting them to the UNIVA grid engine, monitoring the task array execution, and finally merging the individual CellProfiler task output generated on the Linux cluster.

Due to its high dependency on the Novartis cluster infrastructure this job is unlikely to run unmodified on other Linux clusters, but it provides a useful and complete exemplar on how Jenkins-LSCI is used to integrate a complex workflow and monitor an external job.


##Test Drive Jenkins
If you would like to quickly [test drive Jenkins-CI](https://wiki.jenkins-ci.org/display/JENKINS/Meet+Jenkins) you can [download jenkins.war](http://mirrors.jenkins-ci.org/war/latest/jenkins.war) directly and launch it by executing ```java -jar jenkins.war```.  Once it launches, visit ```http://localhost:8080/``` in your browser to get to the dashboard. On Windows, you can even choose to install [Jenkins as a service](https://wiki.jenkins-ci.org/display/JENKINS/Installing+Jenkins+as+a+Windows+service) afterwards.

##Jenkins-LSCI Installation & Configuration
A fully functional Jenkins-LSCI server supporting CellProfiler image analysis requires the installation and configuraton of the following software components:

 1. A mirror of the Jenkins-LSCI project code (from git)
 2. [Jenkins-CI](https://jenkins.io/) and required Jenkins plugins
 3. [CellProfiler](http://cellprofiler.org)


Please, refer to the [Jenkins-LSCI Installation and Usage](./userContent/docs/installation_and_use.md) for more details

##Getting Help
For general assistance with Jenkins-CI you can consult the [Jenkins Google user group](https://groups.google.com/forum/#!forum/jenkinsci-users) and the extensive, community-maintained [Jenkins-CI wiki](https://wiki.jenkins-ci.org/display/JENKINS/Use+Jenkins)

For general assistance with CellProfiler you can consult the [CellProfiler User Group](http://forum.cellprofiler.org/)

The [BioUno](http://biouno.org) open source project and [BioUno Google user group](https://groups.google.com/forum/#!forum/biouno-users) can provide additional guidance and assistance in setting up Jenkins for bioinformatics and data science applications

##License
The Jenkins-LSCI code is provided under the [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) license