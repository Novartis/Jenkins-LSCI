/*
Copyright 2016 Novartis Institutes for BioMedical Research Inc.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/*** BEGIN META {
 "name" : "cpSgeTaskArray",
 "comment" : "Creates a CellProfiler grid engine task array from image groups or image ranges",
 "parameters" : [ 'vImageListProps','vGroupMeta','vPipelineRef','vOutfolder','vBatchByGroup', 'vBuildNumber', 'vBuildId','vTemplatePath'],
 "core": "1.593",
 "authors" : [
 { name : "Ioannis Moutsatsos" }
 ]
 } END META**/

/**
 * Created by moutsio1 on 6/29/2016.
 * Creates a CellProfiler grid engine task array
 * Supports standard batching in groups of 12 as well as
 * batching in custom groups defined by pipeline metadata
 */

def imageListProps=vImageListProps
def groupMeta=vGroupMeta
def pipelineRef=vPipelineRef
def outfolder=vOutfolder
def buildNumber=vBuildNumber
def buildId=vBuildId
def batchByGroup=vBatchByGroup
println "batchByGroup= $vBatchByGroup"

def j_project=pipelineRef.split('#')[0]
def j_build_no=pipelineRef.split('#')[1]
def pipeline="${jenkins.model.Jenkins.instance.getRootUrl()}job/$j_project/$j_build_no/artifact/CP_pipeline_val.cp"
def propertiesURL= new URL(imageListProps)
def props=new Properties() //the image list properties
props.load(propertiesURL.openStream())

def jobSite="${jenkins.model.Jenkins.instance.getRootUrl()}userContent/properties/jobSite.properties"
def jobSiteURL= new URL(jobSite)
def jobSiteProps=new Properties() //the image list properties
jobSiteProps.load(jobSiteURL.openStream())

def batchSize=12
if (jobSiteProps.getProperty('cp.batchsize')!=null){
batchSize=jobSiteProps.getProperty('cp.batchsize') as int
}

def imageRanges= getImageRanges(props, batchSize)
def templatePath=vTemplatePath
def templateBinding=[:]
templateBinding.put('prefix','JCB')
templateBinding.put('buildNo',buildNumber)
templateBinding.put('buildId',buildId)
templateBinding.put('pipeline',pipeline)


def taskArrayRangeTemplate=new File("$templatePath/taskArrayRangeTemplate.txt")
def taskArrayGroupTemplate=new File("$templatePath/taskArrayGroupTemplate.txt")

if (batchByGroup=='true'){
  assert groupMeta!='Metadata_grouping_not_defined'	
  def imageGroups =getImageGroups(props,groupMeta)
    templateBinding.put('taskCount',imageGroups.size())
    templateBinding.put('imageGroups',imageGroups)
    writeTaskArrayScript(taskArrayGroupTemplate, templateBinding, outfolder)
}else{
    assert imageRanges.size()>1
    templateBinding.put('taskCount',imageRanges.size())
    templateBinding.put('imageRanges',imageRanges)
    writeTaskArrayScript(taskArrayRangeTemplate, templateBinding, outfolder)
}

/*method returns an array of formatted imageGroups for CP -g cli-options */
def getImageGroups(imageListProps, groupMeta){
    def groupMap=[:]
    def groupMetaList=groupMeta.split(',')

    groupMetaList.each{
        valArray=(imageListProps."$it").split(',')
        groupMap.put(it, valArray)
    }
//Note how we construct a dynamic closure to be used in the collect method below
    dynaMetaCode=[]
    groupMetaList.eachWithIndex{it,ind->
        dynaMetaCode.add('${g['+ind+']}=${e['+ind+']}')
    }
    dCode= "{e,g->\"${dynaMetaCode.join(',')}\"}"
//now evaluate the dynamic closure
    def metaCode=evaluate(dCode)
//Create required combination from properties
    return GroovyCollections.combinations(groupMap.values()).collect{metaCode(it,groupMetaList)}

}

/*method returns an array of formatted imageRanges for CP -r cli-options */
def getImageRanges(imageListProps, batchSize){
    imageListSize=imageListProps.ImageList_Size as int
    return (1..imageListSize).step(batchSize).collect{"-f$it -l${imageListSize>it+(batchSize-1)?it+(batchSize-1):imageListSize}"}
}


/*

 */
def writeTaskArrayScript(arrayTemplate, templateBinding, outfolder){
    sciptName='cpLaunchSge.sh'
    scriptFileName = "$outfolder/$sciptName"
    println "Writing: $scriptFileName"
    scptFile = new File(scriptFileName)
    scptFileWriter = scptFile.newWriter(false)

    engine = new groovy.text.GStringTemplateEngine()
    sgeArrayTemplate = engine.createTemplate(arrayTemplate)
    scptFileWriter<< sgeArrayTemplate.make(templateBinding)
    scptFileWriter.flush()
    scptFileWriter.close()

}
