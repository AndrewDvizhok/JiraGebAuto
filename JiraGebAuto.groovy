@Grapes([
        @Grab(group='org.gebish', module='geb-core', version='2.3.1'),//always use latest version of geb and selenium drivers
        @Grab("org.seleniumhq.selenium:selenium-firefox-driver:3.141.59"),
        @Grab("org.seleniumhq.selenium:selenium-support:3.141.59")
])
import geb.Browser
import geb.Page
import groovy.json.JsonSlurper
import groovy.transform.TupleConstructor
import groovyjarjarasm.asm.Attribute

// For decrease load ms
int WAIT_BETWEEN_REQUEST = 800

// http://jira.example.com (without last /)
String JIRA_SERVER_URL = 'http://localhost:8087'

//username from who login
String JIRA_USERNAME = 'AndrewDvizhok'
/**
 * Hold info about proj
 */
class Project {
    String key
    String id
    String name
    String issueTypeS
    String workflowS
    String screenS
    String fieldS
    String priorS
    String permS
    String notifS

    Project(){}

    Project(id, key){
        this.key = key
        this.id = id
    }

    Project(id, key, name){
        this.key = key
        this.id = id
        this.name = name
    }

    @Override
    String toString() {
        return "Project(${id}): ${key} '${name}'"
    }

    /**
     *
     * @return string in csv format
     */
    String toCsv(){
        //"id;key;name;issueType;workflow;screen;field;prior;perm;notif;\n"
        return "${id};${key};${name};${issueTypeS};${workflowS};${screenS};${fieldS};${priorS};${permS};${notifS};\n"
    }
}

class RowField extends Field{
    String tabA
    String tabB

    RowField(){
    }

    RowField(Field field){
        super.setId(field.getId())
        super.setName(field.getName())
        super.setType(field.getType())
    }
}

class Field {
    String id
    String name
    String type

    /*public String getId(){return this.id}
    public String getName(){return this.name}
    public String getType(){return this.type}*/
}
/**
 * For keep screen fields
 * Map<'name tab', Set <Field field>>
 */
class Screen {
    String id
    String name
    Map<String,Set> tabs
}

/**
 * For do GEB method
 */
class WebProvider {
    private static url
    private static Browser browser
    private static password
    private static username
    private static File csv
    public static int DELAY_BETWEEN_OPERATION = 500

    public static setPassword(String password){
        if (password == null){
            this.password = System.console().readPassword("[%s]", 'Password:')
        }else{
            this.password = password
        }
    }

    public static setUsername(String username){
        if (username == null){
            this.username = System.console().readLine("[%s]", 'Username:')
        }else{
            this.username = username
        }
    }

    /**
     * To get screen object. Screen has id, name and Map<String, Set<Field>> where Map<tabs on screen, Set <field on tabs>>
     * @param screenId - id for screen
     * @return object screen
     */
    public static Screen getScreen(screenId){
        browser.go url+'/secure/admin/ConfigureFieldScreen.jspa?id='+screenId
        sleep(DELAY_BETWEEN_OPERATION)
        doWebSudo()
        sleep(DELAY_BETWEEN_OPERATION)
        Screen screen = new Screen()
        Map<String,Set<Field>> tabs=[:];
        if (browser.$("b", id: "screenName").displayed){
            //println("work here!!!!!!!!!!!!!!")
            screen.setName(browser.$("b", id: "screenName").text())
            screen.setId(screenId)
            browser.$("div", id: "screen-editor").$("ul").$("li").each {
                if(!it.attr("data-name").isEmpty()){
                    tabs[it.attr("data-name")]=[]
                }
            }
            tabs.each {
                if(!it.key.isEmpty()){
                    browser.$("li", 'data-name': it.key).click()
                    sleep(DELAY_BETWEEN_OPERATION)
                    Set fields = []
                    browser.$("tbody", class: "ui-sortable").$("tr").each {
                        if (it.attr("data-id") != null){
                            Field field = new Field()
                            field.setId(it.attr("data-id"))
                            //println("add field: "+it.attr("data-id"))
                            field.setName(it.attr("data-name"))
                            field.setType(it.attr("data-type"))
                            fields.add(field)
                        }
                    }
                    it.value=fields.clone()
                    //println("how added set: "+it.value+": tab: "+it.key)
                }
            }
            screen.setTabs(tabs.clone())
        }else{
            println("Some wrong with screen: "+screenId)
            //return empty screen
        }
        return screen
    }

    /**
     * Write in the console table of diff between screen
     * @param screenIdA - show on left column
     * @param screenIdB - show on right column
     */
    public static void diffScreen(screenIdA, screenIdB){
        Screen screenA = this.getScreen(screenIdA)
        Screen screenB = this.getScreen(screenIdB)

        Set<RowField> listFields = []
        screenA.getTabs().each {
            def tabA = it.key
            it.value.each {
                RowField rf = new RowField(it)
                rf.setTabA(tabA)
                listFields.add(rf)
            }
        }

        screenB.getTabs().each {
            def tabB = it.key
            it.value.each {
                def fId = it.getId()
                if (listFields.find{it.id == fId}){
                    listFields.find{it.id == fId}.setTabB(tabB)
                }else{
                    RowField rf = new RowField(it)
                    rf.setTabB(tabB)
                    listFields.add(rf)
                }
            }
        }
        println("="*70)
        println("|       Fields        |     Tabs on scrA    |     Tabs on scrB    |")
        println("="*70)
        listFields.each {
            def leng = 20
            if (it.getName().length() < 20) leng = it.getName().length()
            printf("|%-20s | %-20s| %-20s|\n",it.getName().substring(0,leng),it.getTabA(),it.getTabB())
        }
        println("="*70)
    }


    /**
     * make login to site
     * @return tru if all OK
     */
    public static boolean doLogin(){
        //if already logged return true
        if (checkAuth()) return true
        browser.go url+'/secure/Dashboard.jspa'
        //need wait when page loaded
        sleep(DELAY_BETWEEN_OPERATION)
        if (browser.$("form", id: "loginform").displayed){
            browser.$("form", id: "loginform").with{
                os_username = this.username
                os_password = this.password
                login().click()
            }
            return checkAuth()
        }else{
            return false
        }
    }

    /**
     * Page have user info?
     * @return - if has link to user profile that mean we logged
     */
    public static boolean checkAuth(){
        if (browser.$("a", id: "header-details-user-fullname").displayed){
            return true
        }else{
            return false
        }
    }

    /**
     * Jira show us webSudo page?
     * @return - true if show
     */
    public static boolean checkWebSudo(){
        if (browser.$("form", action: "/secure/admin/WebSudoAuthenticate.jspa").displayed){
            return true
        }else{
            return false
        }
    }

    /**
     * Put pass to webSudo
     * @return - true if we see sebSudo
     */
    public static boolean doWebSudo(){
        if (checkWebSudo()){
            browser.$("form", action: "/secure/admin/WebSudoAuthenticate.jspa").with{
                webSudoPassword = password
                $("input", id: "login-form-submit").click()
            }
            return true
        }else{
            return false
        }
    }

    public static boolean deleteProject(id){
        browser.go url+'/secure/project/DeleteProject!default.jspa?pid='+id
        doWebSudo()
        if (browser.$("form", id: "delete-project-confirm").displayed){
            browser.$("form", id: "delete-project-confirm").with{
                delete().click()
            }
            return true
        }else{
            return false
        }
    }

    /**
     * Get map user roles
     * @param user - which we want check
     * @return - map of user roles: Map<String, List<String>> where Map<ProjectKey, List<Roles>>
     */
    public static Map getActiveRoleUser(String user){
        this.getActiveRoleUser(user, null)
    }

    /**
     * Get map user roles and save it to file
     * @param user - which we want check
     * @param csvFile - file to save
     * @return - map of user roles: Map<String, List<String>> where Map<ProjectKey, List<Roles>>
     */
    public static Map getActiveRoleUser(String user, String csvFile){
        if (csvFile!=null){
            csv = new File(csvFile)
        }
        browser.go url+"/secure/admin/user/ViewUserProjectRoles!default.jspa?name=${user}&returnUrl=ViewUser.jspa"
        sleep(DELAY_BETWEEN_OPERATION)
        doWebSudo()
        sleep(DELAY_BETWEEN_OPERATION)
        List roles=[]
        String Sroles=''
        Map projects=[:]
        if (browser.$("h2").text() == 'View Project Roles for User'){
            //Get all roles columns
            browser.$("table", class: "aui aui-table-rowhover role-access")[0].$("th", class: "cell-type-centered").each{
                roles.add(it.text())
                Sroles += it.text()+';'
            }
            projects['Roles']=roles.clone()
            roles=[]

            def prName = 'Default'
            def notFirst=false
            if (csv!=null){
                csv.append('Roles;'+Sroles+'\n')
                Sroles=''
            }
            // Get all roles for each category
            browser.$("tbody").each{it.$("tr").each{
                notFirst=false
                prName = 'Default'
                roles=[]
                Sroles=''
                it.$("td").each{
                    if (notFirst){
                        if(it.attr('class') == 'role-member cell-type-centered'){
                            roles.add(it.$("span").text())
                            Sroles += it.$("span").text()+';'
                        }else{
                            roles.add('0')
                            Sroles += '0;'
                        }
                    }
                    if (it.attr('class') == 'cell-type-key'){
                        prName = it.text()
                    }
                    notFirst=true
                }
                projects[prName]=roles.clone()
                if (csv!=null){
                    csv.append(prName+';'+Sroles+'\n')
                    Sroles=''
                }
            }}

        }
        return projects
    }

    /**
     * Delete issue type scheme
     * @param schemeId - which we want to del
     * @return true if ok, false if some wrong
     */
    public static boolean delIssueTypeS(schemeId){
        browser.go url+"/secure/admin/DeleteOptionScheme!default.jspa?fieldId=&schemeId=${schemeId}"
        doWebSudo()
        sleep(DELAY_BETWEEN_OPERATION)
        if(browser.$("input", name:"Delete").displayed){
            browser.$("input", name:"Delete").click()
            return true
        }else{
            return false
        }
    }

    /**
     * For delete all empty issue types
     */
    public static void delEmtpyIssueTypeS(){

        browser.go url+"/secure/admin/ManageIssueTypeSchemes!default.jspa"
        doWebSudo()
        sleep(DELAY_BETWEEN_OPERATION)
        Set schemes=[]
        browser.$("tbody").$("tr").each{
            if (it.$("td", 'data-scheme-field':"projects").$("span").text() == "No projects"){
                schemes.add(it.attr('data-id'))
                /*if (!this.delIssueTypeS(it.attr('data-id'))){
                    println("Wring delete issue type scheme: "+it.attr('data-id'))
                }else{
                    println("Deleted issue type scheme: "+it.attr('data-id'))
                }*/
            }
        }
        schemes.each{
            if(!this.delIssueTypeS(it)){
                println("Wring delete issue type scheme: "+it)
            }else{
                println("Deleted issue type scheme: "+it)
            }
        }

    }

    public static void delProjectsEx(listPr){
        File exFile = new File(listPr)
        if (exFile.exists()){
            def exList = exFile.readLines()
            this.getProjects().each{
                def keyP = it.key
                if(exList.find{it==keyP}){
                    println("Prject excluded: "+it)
                }else{
                    delProject(it)
                }
            }
        }
    }

    public static void delProjectsList(listPr){
        File exFile = new File(listPr)
        if (exFile.exists()){
            def list = exFile.readLines()
            list.each{
                //sleep(2000)
                def proj = this.getProject(it)
                if (proj.id != null){
                    println("Deleted: ${proj}")
                    sleep(DELAY_BETWEEN_OPERATION)
                    this.delProject(proj)
                }else{
                    println("Warn! Proj not exist: ${it}")
                }
            }
        }
    }


    public static void delProject(project){
        browser.go url+"/secure/project/DeleteProject!default.jspa?pid=${project.id}"
        doWebSudo()
        sleep(DELAY_BETWEEN_OPERATION)
        browser.$("button", id:"delete-project-confirm-submit").click()
    }

    /**
     * Return object Project from his keys. Set id and name.
     * @param key of the project
     * @return object Project
     */
    public static Project getProject(key){
        browser.go url+"/rest/api/2/project/"+key
        sleep(DELAY_BETWEEN_OPERATION)
        browser.$("a", id: "rawdata-tab").click()
        sleep(DELAY_BETWEEN_OPERATION)
        def json = new JsonSlurper().parseText(browser.$("pre", class: "data").text())
        Project proj = new Project(json.id, json.key)
        proj.setName(json.name)
        return proj
    }

    /**
     * Get all projects in Jira
     * @return - set of projects Set<Project>
     */
    public static Set<Project> getProjects(){
        browser.go url+"/rest/api/2/project"
        sleep(DELAY_BETWEEN_OPERATION)
        browser.$("a", id: "rawdata-tab").click()
        sleep(DELAY_BETWEEN_OPERATION)
        def json = new JsonSlurper().parseText(browser.$("pre", class: "data").text())
        Set projects = []
        json.each{
            projects.add(new Project(it.id, it.key, it.name))
        }
        return projects
    }
    /**
     * Fill all schemes of Project. Information get from 'Project settings - Summary'
     * @param projKey - for which we want get all schemes.
     * @return
     */
    public static Project getProjectSchemes(String projKey){
        Project proj = getProject(projKey)
        return getProjectSchemes(proj)
    }

    /**
     * The same as @getProjectSchemes but use object instead string key.
     * @param proj
     * @return object project with all properties.
     */
    public static Project getProjectSchemes(Project proj){
        browser.go url+"/plugins/servlet/project-config/${proj.getKey()}/summary"
        sleep(DELAY_BETWEEN_OPERATION)
        if (browser.$("header", id: "project-config-header").$("h1")[0].text() == 'Project settings'){
            proj.setIssueTypeS(browser.$("div", id: "project-config-webpanel-summary-issuetypes").$("p", class:"project-config-summary-scheme").$("a").text())
            proj.setWorkflowS(browser.$("div", id: "project-config-webpanel-summary-workflows").$("p", class:"project-config-summary-scheme").$("a").text())
            proj.setScreenS(browser.$("div", id: "project-config-webpanel-summary-screens").$("p", class:"project-config-summary-scheme").$("a").text())
            proj.setFieldS(browser.$("div", id: "project-config-webpanel-summary-fields").$("p", class:"project-config-summary-scheme").$("a").text())
            proj.setPriorS(browser.$("div", id: "project-config-webpanel-summary-priorities").$("p", class:"project-config-summary-scheme").$("a").text())
            proj.setPermS(browser.$("a", id: "project-config-permissions").text())
            proj.setNotifS(browser.$("a", id: "project-config-notif").text())
        }
        return proj
    }

    /**
     * Save all schemes for all projects to CSV file.
     * @param fName - CSV file name.
     */
    public static void getAllProjectSchemes(fName){
        File csvFile = new File(fName)
        csvFile.append("id;key;name;issueType;workflow;screen;field;prior;perm;notif;\n")
        print("id;key;name;issueType;workflow;screen;field;prior;perm;notif;\n")
        getProjects().each {
            def line = getProjectSchemes(it).toCsv()
            print(line)
            csvFile.append(line)
        }
    }

    /**
     * Change for projects on list issue type schemes.
     * @param lProj file of list projects
     * @param iType - id issue type scheme
     */
    public static void setProjectsIT(lProj,iType){
        File exFile = new File(lProj)
        if (exFile.exists()){
            def projects = exFile.readLines()
            projects.each {
                browser.go url+"secure/admin/SelectIssueTypeSchemeForProject!default.jspa?projectId=${it}"
                sleep(DELAY_BETWEEN_OPERATION)
                doWebSudo()
                browser.$("option", id: "schemeId_select_${iType}").click()
                browser.$("input", id:"ok_submit").click()
                println("Changed for proj ${it} to ${iType}")
            }
        }
    }

    public static void delEmtpyITS(){
        browser.go url + "/secure/admin/ManageIssueTypeSchemes!default.jspa"
        sleep(DELAY_BETWEEN_OPERATION)
        doWebSudo()
        Set typeList = []
        //get list of empty schemes
        if (browser.$("table", id: "issuetypeschemes").displayed){
            browser.$("tbody").$("tr").each {
                //it.attr("data-id")
                if (it.$("span", class: "errorText").displayed){
                    typeList.add(it.attr("data-id"))
                }
            }
        }

        //delete empty schemes
        typeList.each {
            browser.go url + "/secure/admin/DeleteOptionScheme!default.jspa?fieldId=&schemeId=${it}"
            sleep(DELAY_BETWEEN_OPERATION)
            doWebSudo()
            browser.$("input", id: "delete_submit").click()
            println("Deleted Issue Type Scheme: "+it)
        }
    }

    /**
     * Delete rapid view board with ID = bId. Cannot be used without open rapid board.
     * @param bId - ID of the rapid board for delete
     * @return  TRUE - if board was deleted. FALSE - if something wrong.
     */
    public static boolean delBoard(bId){
        browser.$("button", id: "ghx-manage-boards-operation-trigger-${bId}").click()
        sleep(50)
        browser.$("div", id :"ghx-manage-boards-operation-popup-${bId}").$("a", class: "js-delete-board-action").click()
        sleep(100)
        if (browser.$("button", class: "button-panel-button aui-button").displayed){
            browser.$("button", class: "button-panel-button aui-button").click()
            return true;
        }else{
            return false;
        }
    }

    /**
     * Deleta boards from list of file fName. If user haven't access to board it will not find that board.
     * @param fName - txt file with ID boards for delete.
     */
    public static void delBoards(fName){
        // counters for statistics
        def deletedB = 0
        def tryDelB = 0
        // flag if we stay on last page baords panel
        def lastPage = false
        // flag if we exist pages of boards
        def hasPage = true
        // flag for loop remove process
        def stillWork = true

        File exFile = new File(fName)
        File logF = new File("deleteBoards.txt")
        if (exFile.exists()){
            def boards = exFile.readLines()
            browser.go url + "/secure/ManageRapidViews.jspa"
            sleep(DELAY_BETWEEN_OPERATION)
            doWebSudo()
            // chak that exist pages of boards
            if (browser.$("li", class:"aui-nav-next").displayed){
                hasPage = true
            }else{
                hasPage = false
            }

            // will hold run until not find no one boards for delete
            while(stillWork){
                // flag if we remove board
                def removed = false
                def currentBoards = []
                // get boards on current view
                browser.$("tbody").$("tr").each {
                    currentBoards.add(it.attr("data-board-id"))
                }
                // check all current boards, can we delete one of them
                currentBoards.each {
                    // if we have't pages that mean we can check all current boards
                    if (!removed||!hasPage){
                        def curB = it
                        if (boards.find {it == curB}){
                            // current board have id to delete
                            if (this.delBoard(it)){
                                println("Deleted board: ${it}")
                                logF.append("Deleted board: ${it}\n")
                                deletedB++
                                // after delete needed start again because view can changed and GEB not find button
                                browser.go url + "/secure/ManageRapidViews.jspa"
                                sleep(DELAY_BETWEEN_OPERATION)
                                doWebSudo()
                                // after delete board jira can hide 'next' button. It mean now we have only one page.
                                if (browser.$("li", class:"aui-nav-next").displayed){
                                    hasPage = true
                                }else{
                                    hasPage = false
                                }
                                // stop each and start agian
                                removed = true
                                // after reset we start in first page
                                lastPage = false
                            }else{
                                println("Error delete board: ${it}")
                                logF.append("Error delete board: ${it}\n")
                            }
                            tryDelB++
                        }
                    }
                }

                // if we not remove in this page mean we should try see next page, if next page exist
                if (!removed&&hasPage){
                    // we not remove and should try next page
                    browser.$("li", class:"aui-nav-next").click()
                }

                // we stay in last page and no one removed it mean we cannot find boards, work should be stop
                if (lastPage&&!removed){
                    println("stop work")
                    stillWork = false
                }

                // we check in last step that we stay in last page because we want one more iteration
                if (!hasPage||browser.$("li", class: "aui-nav-next").$("a").attr("aria-disabled") == "true"){
                    lastPage = true
                }
            }
        }
        logF.append("For delete: ${tryDelB} ; Deleted: ${deletedB} ;\n")
    }


}

/**
 * Make work
 */
/**
 * Get argumets from CLI
 */
def TASK_TO_DO=''
if (args.length>0){
    TASK_TO_DO=args[0]
}else{
    println """Wrong parameters! Use:\n 
> groovy JiraGebAuto.groovy getRole userName C:/some.csv 

- for get csv file of all roles for userName;

> groovy JiraGebAuto.groovy delIssueTypes 
- delete all issue type schemes where 'No project';

> groovy JiraGebAuto.groovy delExclude savedList.txt 
- delete ALL project which NOT in savedList.txt
savedList.txt should be as next:
PROJKEYA
PROJKEYB

> groovy JiraGebAuto.groovy delList toDelete.txt 
- delete ALL project which IN toDelete.txt
toDelete.txt should be as next:
PROJKEYA
PROJKEYB

> groovy JiraGebAuto.groovy diffScreen screenIdA screenIdB
- show table diff between two screen
Example:
>groovy.bat JiraGeb.groovy diffScreen 10000 1
diffScreen
======================================================================
|       Fields        |     Tabs on scrA    |     Tabs on scrB    |
======================================================================
|Summary              | Field Tab           | Field Tab           |
|Issue Type           | Field Tab           | null                |
|Reporter             | test                | Field Tab           |


> groovy JiraGebAuto.groovy getSchemes file.csv
- save all projects with schemes to csv file

> groovy JiraGebAuto.groovy delEmptyITS
- delete all IssueTypeSchemes eith 'No Project'

>groovy JiraGebAuto.groovy setPIT list.txt idNum
- change issue types for project in lists.
Example:
>groovy.bat JiraGeb.groovy setPIT list.txt 10714

> groovy JiraGebAuto.groovy delBoards list.txt
- delete rapid boards from list.txt.
list.txt should be as next:
23
27


"""
    return 1
}

println 'Need username/password...'

def browser = new Browser()

WebProvider.url = JIRA_SERVER_URL
//if want hide pass change to WebProvider.setPassword(null)
WebProvider.setPassword('test')
WebProvider.setUsername(JIRA_USERNAME)
WebProvider.browser = browser

browser.go JIRA_SERVER_URL

sleep(WebProvider.DELAY_BETWEEN_OPERATION)
println 'We are logged? ' + WebProvider.checkAuth()
sleep(WebProvider.DELAY_BETWEEN_OPERATION)
println 'Loginng success? ' + WebProvider.doLogin()
sleep(WebProvider.DELAY_BETWEEN_OPERATION)
println 'We are logged? ' + WebProvider.checkAuth()



switch(TASK_TO_DO) {

    case 'getRole':
        if(args.length>2)
            if (args[1]!=null && args[2]!=null){
                println(WebProvider.getActiveRoleUser(args[1],args[2]))
            }
        break

    case 'delIssueTypes':
        WebProvider.delEmtpyIssueTypeS()
        break

    case 'delExclude':
        if(args.length>1)
            if (args[1]!=null){
                WebProvider.delProjectsEx(args[1])
            }
        break
    case 'delList':
        if(args.length>1)
            if (args[1]!=null){
                WebProvider.delProjectsList(args[1])
            }
        break

    case 'diffScreen':
        println ("diffScreen")
        if(args.length>2)
            if (args[1]!=null && args[2]!=null){
                WebProvider.diffScreen(args[1],args[2])
            }
        break
    case 'getSchemes':
        println ("getSchemes")
        if(args.length>1)
            if (args[1]!=null){
                WebProvider.getAllProjectSchemes(args[1])
            }
        break

    case 'setPIT':
        println("change IssueType for projects")
        if(args.length>1)
            if (args[1]!=null&&args[2]!=null){
                WebProvider.setProjectsIT(args[1],args[2])
            }
        break

    case 'delEmptyITS':
        println("delete emtpy Issue Types Scheme")
        WebProvider.delEmtpyITS()
        break

    case 'delBoards':
        println("delete boards from list")
        if(args.length>1)
            if (args[1]!=null){
                WebProvider.delBoards(args[1])
            }
        break
}

println 'Done'

sleep(WebProvider.DELAY_BETWEEN_OPERATION)
browser.quit()