asUser = require('./asUser')
testMethods = require('./testMethods')
wd = require('wd')

Url =
  index: '/documentsets'
  csvUpload: '/imports/csv'

module.exports = (title, csvPath) ->
  testMethods.usingPromiseChainMethods
    goToFirstDocumentSet: ->
      @
        .get(Url.index)
        .waitForElementByCss('.document-sets h3 a, .document-sets h6 a').click()
        .waitForElementByCss('canvas')

    waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 15000)

    wds_openCsvUploadPage: ->
      @
        .get(Url.csvUpload)
        .waitForJqueryReady()

    wds_chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .elementByCss('input[type=file]').sendKeys(fullPath)

    wds_waitForRequirements: ->
      @
        .waitForFunctionToReturnTrueInBrowser(-> $('.requirements li.ok').length == 4 || $('.requirements li.bad').length > 0)

    wds_doUpload: ->
      @
        .elementBy(tag: 'button', contains: 'Upload').click()
        .waitForUrl(Url.index, 10000)

    wds_chooseAndDoUpload: (path) ->
      @
        .wds_chooseFile(path)
        .wds_waitForRequirements()
        .wds_doUpload()

    wds_waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 10000)

  asUser.usingTemporaryUser
    title: title

    before: ->
      @userBrowser
        .wds_openCsvUploadPage()
        .wds_chooseAndDoUpload(csvPath)
        .wds_waitForJobsToComplete()

    after: ->
      @userBrowser
        .get(Url.index)
        .elementByCss('.actions .dropdown-toggle').click()
        .acceptingNextAlert()
        .elementByCss('.delete-document-set').click()
