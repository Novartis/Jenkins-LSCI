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

/*
Given a user contributed pipeline and an optional image List file
auto-generate pipeline properties and write a Summary XML report
Depends on: Summary Display Plugin
Depends on: autoReviewPipeline.groovy
Author Ioannis K. Moutsatsos
Since JUN-25-2013
Last update AUG-02-2016
*/

def env = System.getenv()
def jUrl=env['JENKINS_URL']
def jHome=env['JENKINS_HOME']
def pipelineFileParameterPath=env['WORKSPACE']+'/user_pipeline.cp'
def imageListFileParameterPath=env['WORKSPACE']+'/example_image_list.csv'
def inputImageList=env['example_image_list.csv']

def fileParameter=new File(pipelineFileParameterPath) // pipeline path
assert fileParameter.exists(), "ERROR: Can't access pipeline file: \n\t$pipelineFileParameterPath"

/*Validate and adapt pipeline to new image source folder */
pipeReview=new autoReviewPipeline()
println "Example: $inputImageList"
if(inputImageList!=null){
println 'Input: Provided Image List'
def imageParameter=new File(imageListFileParameterPath) // image path
assert imageParameter.exists(), "ERROR: Can't access pipeline file: \n\t$imageListFileParameterPath"
pipeReview.main("-p${fileParameter.canonicalPath}","-i${imageParameter.canonicalPath}", "-d${env['WORKSPACE']}")
}else{
println 'Input: Missing Image List'
pipeReview.main("-p${fileParameter.canonicalPath}", "-d${env['WORKSPACE']}")
}