# This shows how the current application can be forked into a separate
# application.

import javax

# fork the application
appmodel= ApplicationModel()
appmodel.addDasPeersToApp()

ui= AutoPlotUI(appmodel)
ui.defaultCloseOperation= javax.swing.JFrame.DISPOSE_ON_CLOSE
ui.visible= True

dom2= appmodel.getDocumentModel()

dom2.syncTo( dom, java.util.Arrays.asList( [ 'id' ]) )

