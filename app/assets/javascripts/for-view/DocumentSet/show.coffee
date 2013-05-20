define [
  'jquery'
  'apps/Tree/app'
], ($, App) ->
  $ ->
    el = (id) -> document.getElementById(id)

    new App({
      tagListEl: el('tag-list')
      focusEl: el('focus')
      treeEl: el('tree')
      documentListEl: el('document-list-container')
      documentEl: el('document')
      navEl: document.getElementsByTagName('nav')[0]
      fullSizeEl: el('main')
      innerFullSizeEl: $('#main>.flex-row')[0]
    })
