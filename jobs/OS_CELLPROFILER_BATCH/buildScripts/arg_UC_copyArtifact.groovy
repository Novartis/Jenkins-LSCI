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
  "name" : "UC_copyArtifact.groovy",
  "comment" : "Will copy selected files from a LOCAL JOB. Uses the uno-choice JOB#BUILD_NUMBER convention. Files can be selected from either the build archive or fileParameters using the buildFolder",
  "parameters" : [ 'vJob_BuildNumber','vArtifactFilter','vBuildFolder'],
  "core": "1.593",
  "authors" : [
    { name : "Ioannis Moutsatsos" }
  ]
} END META**/

/*
Script requires parameters: job_buildNumber, artifactFilter, buildFolder
buildFolder options: archive, fileParameters
*/

/*Note that this script runs outside the JenkinsJVM
it has been modified not to use the Jenkins executable thread directly
*/

jenkinsURL='http://localhost:8080/'
def jobBuild=''
def artifactFilter=''
def buildFolder=''

if (args){
	jobBuild=args[0].split('#')
	artifactFilter=args[1]
	buildFolder=args[2]
}else{
	jobBuild=vJob_BuildNumber.split('#')
	artifactFilter=vArtifactFilter
	buildFolder=vBuildFolder
}

def buildNum=jobBuild[1]
def jobName=jobBuild[0]



def buildURL="${jenkinsURL}job/$jobName/$buildNum/"

options=System.getenv()
  
println "JENKINS_HOME: ${options.JENKINS_HOME}"
  def jHome=options.JENKINS_HOME
  def apiCall="${buildURL}api/xml?"
  println apiCall
 //Setup a connection to pull data in with REST
  def url=new URL("$apiCall")
  def connection = url.openConnection()
  	connection.setRequestMethod("GET")
  	connection.connect()
  def returnMessage = ""

  if (connection.responseCode == 200 || connection.responseCode == 201){
    returnMessage = connection.content.text
//print out the full response 
	//println returnMessage
//parse the xml response
    def freeStyleBuild = new XmlSlurper().parseText(returnMessage)
    def buildID=freeStyleBuild.id
    def fileParam="user_pipeline.cp"
    urlParts=freeStyleBuild.url.toString().split('/')
/*Job name extraction from URL */
    jobName=urlParts[4].replace('%20',' ') //replace URL spaces
    
    jPath="$jHome\\jobs\\$jobName\\builds\\$buildID\\$buildFolder"

/* Can get and print key-values for used parameters */ 
println "Here are your build parameters:"   
	    freeStyleBuild.action.parameter.each{
	    println "${it.name} : ${it.value}${it.originalFileName}"
	    }

	println "Copying Files from Path:\n\t $jPath"
          
//selective copy using ant
          def ant=new AntBuilder()
          fileFilter=artifactFilter.split(',')
          fileFilter.each{it->
            filter=it.trim()
          ant.copy(todir:"${options.WORKSPACE}", overwrite:true){
             fileset(dir:jPath){
             include(name:"$filter") 
               }
            }
            
          }//end each
          
  } else {
    println "Error Connecting to " + url
  }