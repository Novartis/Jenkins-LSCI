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
 * A script that selects image sets from an image list based on typical metadata
 * Where Clause is formatted from a query plan generated in Jenkins
 * Writes the new image list that can be cleanly processed with CellProfiler
 * Case Sensitive Header required for CellProfiler
 * Author Ioannis K. Moutsatsos
 * DMPQM-467
 * Since May 13, 2016
 * Last update Sept 13, 2016
 */

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal.LongOverflow;

import groovy.sql.Sql
import org.h2.Driver
import java.util.logging.Logger
import java.util.logging.Level

def cli = new CliBuilder(usage:'selectOnImageListMeta.groovy -i -q [-o -h]')
cli.with{
    h longOpt: 'help', 'Filters an image list using metadata fields'
    i longOpt: 'input', args:1, argName: 'input image list path or url', 'Path or URLl to image list', required:true
    o longOpt: 'output', args:1, argName: 'output file','Output path for saving image list'
    q longOpt: 'queryPlan' , args:1, argName: 'query plan list of maps', 'Query plan entries'

}
def options = cli.parse(args)

def _logger = Logger.getLogger( "INFO",	null)
/*default options for CSVWRITE H2 function */
def dd="fieldDelimiter="
def cs="caseSensitiveColumnNames=true"
//def options= new HashMap()

/* Build gets input from Jenkins via environment variables*/

def _outputFolder=new File(options.o) 
def inputCsvPath=options.i //a URL or Path
def queryKey=''
if (options.q.startsWith('System')){
    queryKey=evaluate(options.q)
}else{
    queryKey=options.q
}
def ignoreColumns='' //unused here


def _inputCsvFile = null //gets defined later as either a File or a URL
def isUrl = false
if (inputCsvPath.startsWith('http:')) {
    //CSV file from url
    _inputCsvFile = inputCsvPath.toURL()
    isUrl = true
} else { //CSV file form local path
    _inputCsvFile = new File(inputCsvPath)
    assert _inputCsvFile.exists()
    println  "Image List from Jenkins Store: $_inputCsvFile.name"
}


//template is now internally generated
def _inputSqlTemplate= 'Select * from $table $whereClause'
//def suffixFromTemplate= 'Subset'


/* Create an h2 in-memory db, calling it what you like, here db1 */
def sql = Sql.newInstance("jdbc:h2:mem:db1","org.h2.Driver")
def selstmt=''

def csvTableName='linux_imageList'
//_inputCsvFile.name.lastIndexOf('.').with {it != -1 ? _inputCsvFile.name[0..<it] : _inputCsvFile.name}.replaceAll(/ /, '').replaceAll(/-/, '_')

typeString = inferColumnTypes("$_inputCsvFile",20,ignoreColumns,isUrl)
stmt = "create table $csvTableName($typeString) as select * from csvread('$_inputCsvFile')" as String

//	_logger.info("Creating table: $csvTableName from CSV as: $stmt")
sql.execute(stmt)

/* initial template bindings, they get expanded from header of csv test definition */
def binding=[
        table:"$csvTableName",
        whereClause:"${makeWhereClause(queryKey)}"
]
selstmt=getSQLStatement(_inputSqlTemplate, binding)
//targetCsv=_outputFolder+"/"+csvTableName+"_${suffixFromTemplate}.csv"
targetCsv=_outputFolder.canonicalPath+"/"+csvTableName+".csv"
sql.execute("CALL CSVWRITE($targetCsv, $selstmt,  $dd, $cs);")
subsetFile=new File(targetCsv)
assert subsetFile.exists()
println "Image list subset saved as:"
println "\t ${subsetFile.canonicalPath}"
//	finally drop the in memory tables
dropStmt="DROP TABLE $csvTableName"
//	_logger.info("Executing: $dropStmt")
sql.execute("DROP TABLE "+csvTableName)
//	_logger.info("Finished dropping: $csvTableName ")
//	_logger.info("Created new CSV Result Set: $targetCsv\n")



/****************************************************************************
 *                              Methods
 ****************************************************************************/
/*
 * Parameterizes and Writes a series of CellProfiler modules (a pipeline) to an output file
 */
def getSQLStatement(sqlTemplate, templateBinding){
    def engine= new groovy.text.SimpleTemplateEngine()
    //now iterate over each of the modules to be included with each variation
    def sqlStatementTemplate=engine.createTemplate(sqlTemplate)
    sqlStatement=sqlStatementTemplate.make(templateBinding).toString()
    println sqlStatement
    return sqlStatement
}



/**
 * Parse the query into it's component clauses.  All
 * queries are assumed to be in the form:
 *
 * select X from Y where Z
 *
 * This function returns X,Y,Z.
 */
def getFields(params){
    bits = params.split(/\s+/)
    def selectIdx,fromIdx,whereIdx = 0
    bits.eachWithIndex{b,i->
        if (b =~ /(?i)select/) selectIdx = i
        if (b =~ /(?i)from/) fromIdx = i
        if (b =~ /(?i)where/) whereIdx = i
    }
    X = bits[selectIdx+1..fromIdx-1].join(" ")
    Y = bits[fromIdx+1..whereIdx-1].join(" ")
    Z = bits[whereIdx+1..-1].join(" ")

    return([X.trim(),Y.trim(),Z.trim()])
}



/* a function to create the Where Clause of a query from a comma delimited list of key=Value pairs
 * author Ioannis K. Moutsatsos
 * Mar-12-2013
 */

def makeWhereClause(PLAN_ENTRIES){
    queryPlan="[${PLAN_ENTRIES.replace('][','],[')}]"
    planAction=evaluate(queryPlan)
    assert planAction.class==java.util.ArrayList
    assert planAction.size()>0
    termList=[]
    planAction.each{
        thisOperator=(it.operator==null)?'=':(it.operator)
        thisOperator=thisOperator.replace(' ','')//SQL operators do not contain spaces
        termList.add('\"'+it.property+'\"'+thisOperator+'\''+ it.value + '\'')

    }
    clause="WHERE ${termList.join(' AND ')}"
    println clause
    return clause
}

/**
 * Infer the type of each column in the file by sampling the first sampleRows
 * rows of data.  The three types supported are:  INT, DOUBLE, VARCHAR.
 * It tries to assign types in order INT, DOUBLE, VARCHAR, assigning each
 * column the strictest type that has uninamous vote in the sample.
 *
 * A negative sampleRows indicates to use whole file as sample.  This is
 * the default, but may want to sample smaller for performance, especially
 * if you know that first few lines are representative.
 *
 * Handling empty fields is a problem that needs to be thought through.
 *
 * Ironic that this is the largest part of the code.

 */
def inferColumnTypes(fileName, sampleRows = 1000, excluded, isUrl) {

    columnType = [:]
    inCsv = null

    if (isUrl) {
        inCsv = fileName.toURL()
        println "inferring types from URL: reading $sampleRows sample rows"
    } else {
        inCsv = new File(fileName)
        println "inferring types from file: reading $sampleRows sample rows"
    }

    numLines = countLines(inCsv, isUrl)
    if (sampleRows < 0) sampleRows = numLines // Use whole file.
    if (sampleRows > numLines) sampleRows = numLines


    inCsv.withReader { r ->
        headings = r.readLine().split(",")
        intcounts = new int[headings.size()]
        doublecounts = new int[headings.size()]

        (0..<sampleRows - 1).each {
            line = r.readLine()
            if (line.equals('')||line.split(",", -1).size()<headings.size() ) {
                line = r.readLine() //read another line for Url fix

            }
            fields = line.split(",", -1) // -1 to handle empty fields.
            assert fields.size() <= headings.size()
            //Sanity test..
            if (fields.size() != headings.size()) {
                throw new Exception("ERROR:  headings size != fields.size().")
            }

            fields.eachWithIndex { f, i ->
                if (f.isDouble()) doublecounts[i]++
                if (f.isInteger()) intcounts[i]++
            }
        }

        headings.eachWithIndex { h, i ->
            if (intcounts[i] == (sampleRows - 1)) columnType[h] = 'INT'
            else if (doublecounts[i] == (sampleRows - 1)) columnType[h] = 'DOUBLE'
            else columnType[h] = 'VARCHAR'
        }
    }

    // Convert into a string...
    pairs = []
    pairsIgnore = [] //from the excluded list of columns
    println 'Checking Columns to Ignore...'
    columnType.each { key, value ->
        if (key in excluded) {
            pairsIgnore << "$key $value"
            println "\t $key on Exclusion list"
        } else {
            pairs << "\"$key\" $value"
        }
    }
    str = pairs.join(",")
    return (str.replace ('""','"'))
}


def int countLines(fileName, isUrl) {
    InputStream bis = fileName.newInputStream() //groovy jdk enhancement, creates a buffered input stream
    int numRows = fastCountLines(bis);
    bis.close();
    return (numRows);
}

/*****************************************
 * An optimized function to quickly count the number of lines remaining
 * in the given input stream
 */
def public int fastCountLines(InputStream is) throws IOException {
    byte[] c = new byte[1024];
    int count = 0;
    int readChars = 0;
    boolean lastCR = true;
    while ((readChars = is.read(c)) != -1) {
        for (int i = 0; i < readChars; ++i) {
            if (c[i] == '\n') ++count;
        }
        // If the last thing we read was a CR, note the fact...
        if (c[(readChars - 1)] == '\n') lastCR = true;
        else lastCR = false;
    }

    // If the very last thing we read wasn't a CR, then the last line doesn't
    // end in a CR and we've undercounted the lines by one...
    if (!lastCR) count++;

    return count;
}
/*
a function to read a particular file line
Issue: Note that it can work from a URL file but we need to skip over carriage returns
ReadLine introduces a blank line after each line read

*/

def String readLineN(inputCsv, int lineNo, isUrl) {
    ret = ''
    switch (isUrl) {
        case true:
            println 'Send a URL'
            inputCsv.withReader { r ->
                (lineNo).times { t ->
                    thisLine = r.readLine()
                    if (thisLine.equals('')) {
                        // println 'line terminator'
                    } else {
                        ret = thisLine
                    }
                }
            }

            break
        case false:
            inputCsv.withReader { r ->

                lineNo.times {
                    ret = r.readLine()
                }

            }

    }
    return ret

}





