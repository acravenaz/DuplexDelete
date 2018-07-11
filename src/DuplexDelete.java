import java.awt.EventQueue;
import javax.swing.*;
import javax.swing.GroupLayout.*;
import javax.swing.LayoutStyle.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.tree.*;

public class DuplexDelete extends JFrame implements ActionListener{

	private static final long serialVersionUID = 1L;
	private static final int INITIAL_STATE = 0;
	private static final int SEARCH_IN_PROGRESS = 1;
	private static final int SEARCH_DONE = 2;
	private static final int DELETE_IN_PROGRESS = 3;
	
	private enum SearchState {SEARCH_NOT_STARTED, SEARCH_DONE, SEARCH_IN_PROGRESS};
	private SearchState searchState = SearchState.SEARCH_NOT_STARTED;
	private boolean thread1Finished = false;
	private boolean thread2Finished = false;
	private boolean stopSearch = false;
	
	private JTextField fieldParentDir;
	private JTextArea thread1Output;
	private JTextArea thread2Output;
	private JTextPane consoleOutput;
	private JCheckBox includeFolders;
	private JButton btnStartDelete;
	private JButton btnListFolders;
	private JButton btnAddSelection;
	private JButton btnRemoveSelection;
	
	private TreeSet<Path> deleteFileList = new TreeSet<Path>();
	private TreeSet<Path> deleteFolderList = new TreeSet<Path>();
	private TreeSet<Path> folderList = new TreeSet<Path>();
	
	private DefaultTreeModel folderTreeModel;
	private DefaultListModel<Path> selectionListModel;
	private JList<Path> selectionList;
	private JTree foldersTree;
	

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
			    try {
		        UIManager.setLookAndFeel(
		            UIManager.getSystemLookAndFeelClassName());
			    } 
			    catch (UnsupportedLookAndFeelException e) {
			    	e.printStackTrace();
			    }
			    catch (ClassNotFoundException e) {
			    	e.printStackTrace();
			    }
			    catch (InstantiationException e) {
			    	e.printStackTrace();
			    }
			    catch (IllegalAccessException e) {
			    	e.printStackTrace();
			    }
				try {
					DuplexDelete frame = new DuplexDelete();
					frame.setVisible(true);
					frame.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void run() {
		ConsoleOutputStream outputStream = new ConsoleOutputStream();
		ErrorOutputStream errorStream = new ErrorOutputStream();
		System.setOut(new PrintStream(outputStream));
		System.setErr(new PrintStream(errorStream));
		setUIState(INITIAL_STATE);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(btnListFolders)) {
			listFolders();
		}
		if (e.getSource().equals(btnAddSelection)) {
			TreePath[] paths = foldersTree.getSelectionPaths();
			for (TreePath path : paths) {
				Path element = (Path)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
				if (!selectionListModel.contains(element)) {
					selectionListModel.addElement(element);
				}
			}
		}
		if (e.getSource().equals(btnRemoveSelection)) {
			List<Path> selections = selectionList.getSelectedValuesList();
			for (Path selection : selections) {
				selectionListModel.removeElement(selection);
			}
		}
		
		if (e.getSource().equals(btnStartDelete)) {
			startDelete();
		}
	}
	
	private void listFolders() {
		switch (searchState) {
		case SEARCH_NOT_STARTED:
			Path parentPath = Paths.get(fieldParentDir.getText());
			folderTreeModel.setRoot(new DefaultMutableTreeNode(parentPath));
			if (Files.isReadable(parentPath)) {
				System.out.format("%s: read successfully.%n", parentPath);
				Thread searchThread = new Thread(new Runnable() {
					public void run() {
						searchState = SearchState.SEARCH_IN_PROGRESS;
						setUIState(SEARCH_IN_PROGRESS);
						DirectoryLister lister = new DirectoryLister();
						try {
							Files.walkFileTree(parentPath, lister);
						} catch (IOException exc) {
							exc.printStackTrace();
						}
						searchState = SearchState.SEARCH_DONE;
						setUIState(SEARCH_DONE);
					}
				});
				searchThread.start();
			} else {
				System.err.format("%s: directory not readable.%n", parentPath);
			}
			break;
		case SEARCH_IN_PROGRESS:
			stopSearch = true;
			searchState = SearchState.SEARCH_DONE;
			setUIState(SEARCH_DONE);
			break;
		case SEARCH_DONE:
			searchState = SearchState.SEARCH_NOT_STARTED;
			setUIState(INITIAL_STATE);
			break;
		}
	}
	
	private void startDelete() {
		deleteFileList.clear();
		deleteFolderList.clear();
		folderList.clear();
		setUIState(DELETE_IN_PROGRESS);
		Enumeration<Path> selections = selectionListModel.elements();
		while (selections.hasMoreElements()) {
			Path selection = selections.nextElement();
			if (Files.exists(selection)) {
				if (Files.isDirectory(selection)) {
					folderList.add(selection);
				} else if (Files.isRegularFile(selection, LinkOption.NOFOLLOW_LINKS)) {
					deleteFileList.add(selection);
				}
			}
		}
		
		FileLister lister = new FileLister();
		for (Path p : folderList) {
			try {
				Files.walkFileTree(p, lister);
			} catch (IOException exc) {
				exc.printStackTrace();
			}
		}
		thread1Finished = false;
		thread2Finished = false;
		Thread thread1 = new Thread(new Runnable() {
			public void run() {
				deleteFiles(false, thread1Output);
				thread1Finished = true;
			}
		});
		Thread thread2 = new Thread(new Runnable() {
			public void run() {
				deleteFiles(true, thread2Output);
				thread2Finished = true;
			}
		});
		thread1.start();
		thread2.start();
		while (!thread1Finished && !thread2Finished) {
			sleep(100);
		}
		deleteFolders();
		searchState = SearchState.SEARCH_NOT_STARTED;
		setUIState(INITIAL_STATE);
	}
	
	private void deleteFiles(boolean reverse, JTextArea output) {
		if (!deleteFileList.isEmpty()) {
			Iterator<Path> deleteQueue = reverse ? deleteFileList.iterator() : deleteFileList.descendingIterator();
			int i = 0;
			while (deleteQueue.hasNext() && i < (deleteFileList.size() / 2) + 1) {
				Path p = deleteQueue.next();
				try {
					Files.delete(p);
					//output.append(p + ": deleted successfully.\n");
				} catch (NoSuchFileException e) {
					output.append(p + ": no such file or directory.\n");
				} catch (DirectoryNotEmptyException e) {
					output.append(p + ": directory not empty!\n");
				} catch (IOException e) {
					// File permission problems are caught here.
					output.append(e.toString() + "\n");
				}
				i++;
			}
		}
	}
	
	private void deleteFolders() {
		for (Path dir : deleteFolderList) {
			FolderCleaner cleaner = new FolderCleaner();
			try {
				Files.walkFileTree(dir, cleaner);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void setUIState(int state) {
		switch (state) {
		case INITIAL_STATE:
			btnStartDelete.setEnabled(false);
			btnAddSelection.setEnabled(false);
			btnRemoveSelection.setEnabled(false);
			fieldParentDir.setEnabled(true);
			folderTreeModel.setRoot(new DefaultMutableTreeNode(""));
			selectionListModel.clear();
			foldersTree.setEnabled(true);
			selectionList.setEnabled(true);
			btnListFolders.setEnabled(true);
			btnListFolders.setText("List Folders");
			break;
		case SEARCH_DONE:
			btnStartDelete.setEnabled(true);
			btnListFolders.setEnabled(true);
			btnListFolders.setText("Start Over");
			btnAddSelection.setEnabled(true);
			btnRemoveSelection.setEnabled(true);
			break;
		case SEARCH_IN_PROGRESS:
			btnListFolders.setText("Stop");
			fieldParentDir.setEnabled(false);
			break;
		case DELETE_IN_PROGRESS:
			btnAddSelection.setEnabled(false);
			btnRemoveSelection.setEnabled(false);
			btnStartDelete.setEnabled(false);
			btnListFolders.setEnabled(false);
			foldersTree.setEnabled(false);
			selectionList.setEnabled(false);
			break;
		}
	}
	
	private DefaultMutableTreeNode getParentNode(Path child) {
		Path parentPath = child.getParent();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) folderTreeModel.getRoot();
		if (parentPath.equals(root.getUserObject())) {
			return root;
		}
		return nodeSearch(root, parentPath);
	}

	
	private DefaultMutableTreeNode nodeSearch(DefaultMutableTreeNode startNode, Path matchPath) {
		DefaultMutableTreeNode returnNode = null;
		int i = 0;
		while (returnNode == null && i < startNode.getChildCount()) {
			DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)startNode.getChildAt(i);
			if (childNode.getUserObject().equals(matchPath)) {
				returnNode = childNode;
			} else {
				returnNode = nodeSearch(childNode, matchPath);
			}
			i++;
		}
		return returnNode;
	}
	
	private class DirectoryLister extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) throws IOException {
			if (!dir.equals(Paths.get(fieldParentDir.getText()))) {
				//System.out.println("Directory added: " + dir);
				DefaultMutableTreeNode parent = getParentNode(dir);
				if (parent != null) {
					folderTreeModel.insertNodeInto(new DefaultMutableTreeNode(dir), parent, parent.getChildCount());
				}
			}
			if (stopSearch == true) {
				stopSearch = false;
				return FileVisitResult.TERMINATE;
			}
			return FileVisitResult.CONTINUE;
		}
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
			if (!file.equals(Paths.get(fieldParentDir.getText()))) {
				//System.out.println("File added: " + file);
				DefaultMutableTreeNode parent = getParentNode(file);
				if (parent != null) {
					folderTreeModel.insertNodeInto(new DefaultMutableTreeNode(file), parent, parent.getChildCount());
				}
			}
			if (stopSearch == true) {
				stopSearch = false;
				return FileVisitResult.TERMINATE;
			}
			return FileVisitResult.CONTINUE;
		}
		@Override
		public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
			System.err.println(e);
			if (stopSearch == true) {
				stopSearch = false;
				return FileVisitResult.TERMINATE;
			}
			return FileVisitResult.CONTINUE;
		}
		
	}
	
	private class FileLister extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) throws IOException {
			
			deleteFolderList.add(dir);
			//sleep(1);
			return FileVisitResult.CONTINUE;
		}
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
			deleteFileList.add(file);
			//sleep(1);
			return FileVisitResult.CONTINUE;
		}
		@Override
		public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
			System.err.println(e);
			return FileVisitResult.CONTINUE;
		}
		
	}
	
	private class FolderCleaner extends SimpleFileVisitor<Path> {

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			if (includeFolders.isSelected() ? true : !folderList.contains(dir)) {
				try {
					Files.delete(dir);
					System.out.println(dir + ": deleted successfully.");
				} catch (NoSuchFileException e) {
					System.out.println(dir + ": no such file or directory.");
				} catch (DirectoryNotEmptyException e) {
					System.out.println(dir + ": directory not empty!");
				} catch (IOException e) {
					// File permission problems are caught here.
					System.err.println(e.toString() + "\n");
				}
			}
			return FileVisitResult.CONTINUE;
		}
	}
	
	private class ConsoleOutputStream extends OutputStream {
	    @Override
	    public void write(byte[] buffer, int offset, int length) throws IOException
	    {
	        final String text = new String (buffer, offset, length);
	        SwingUtilities.invokeLater(new Runnable ()
	            {
	                @Override
	                public void run() 
	                {
	                	try {
	                		SimpleAttributeSet attributes = new SimpleAttributeSet();
	                		Document doc = consoleOutput.getDocument();
	                		doc.insertString(doc.getLength(), text, attributes);
	                		consoleOutput.setCaretPosition( doc.getLength() );
	                	} catch (BadLocationException e) {
	                		e.printStackTrace();
	                	}
	                }
	            });
	    }

	    @Override
	    public void write(int b) throws IOException
	    {
	        write (new byte [] {(byte)b}, 0, 1);
	    }
		
	}
	
	private class ErrorOutputStream extends OutputStream {
	    @Override
	    public void write(byte[] buffer, int offset, int length) throws IOException
	    {
	        final String text = new String (buffer, offset, length);
	        SwingUtilities.invokeLater(new Runnable ()
	            {
	                @Override
	                public void run() 
	                {
	                	try {
	                		SimpleAttributeSet attributes = new SimpleAttributeSet();
	                		attributes.addAttribute(StyleConstants.CharacterConstants.Foreground, Color.RED);
	                		Document doc = consoleOutput.getDocument();
	                		doc.insertString(doc.getLength(), text, attributes);
	                		consoleOutput.setCaretPosition( doc.getLength() );
	                	} catch (BadLocationException e) {
	                		e.printStackTrace();
	                	}
	                }
	            });
	    }

	    @Override
	    public void write(int b) throws IOException
	    {
	        write (new byte [] {(byte)b}, 0, 1);
	    }
		
	}
	
	public void sleep(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Create the frame.
	 */
	public DuplexDelete() {
		setTitle("DuplexDelete");
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 500, 700);
		JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JPanel settingsPanel = new JPanel();
		settingsPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Setup", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
		settingsPanel.setBounds(10, 11, 474, 88);
		contentPane.add(settingsPanel);
		
		JLabel lblParentDir = new JLabel("Parent Directory:");
		
		fieldParentDir = new JTextField();
		fieldParentDir.setColumns(10);
		
		btnStartDelete = new JButton("Start Delete");
		btnStartDelete.setEnabled(false);
		btnStartDelete.addActionListener(this);
		
		includeFolders = new JCheckBox("Include folders in delete");
		
		btnListFolders = new JButton("List Folders");
		btnListFolders.addActionListener(this);
		GroupLayout gl_settingsPanel = new GroupLayout(settingsPanel);
		gl_settingsPanel.setHorizontalGroup(
			gl_settingsPanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_settingsPanel.createSequentialGroup()
					.addContainerGap()
					.addGroup(gl_settingsPanel.createParallelGroup(Alignment.TRAILING)
						.addGroup(gl_settingsPanel.createSequentialGroup()
							.addComponent(lblParentDir)
							.addGap(18)
							.addComponent(fieldParentDir, GroupLayout.DEFAULT_SIZE, 341, Short.MAX_VALUE)
							.addContainerGap())
						.addGroup(gl_settingsPanel.createSequentialGroup()
							.addComponent(includeFolders)
							.addPreferredGap(ComponentPlacement.RELATED, 129, Short.MAX_VALUE)
							.addComponent(btnListFolders)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnStartDelete))))
		);
		gl_settingsPanel.setVerticalGroup(
			gl_settingsPanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_settingsPanel.createSequentialGroup()
					.addGap(5)
					.addGroup(gl_settingsPanel.createParallelGroup(Alignment.BASELINE)
						.addComponent(fieldParentDir, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblParentDir))
					.addPreferredGap(ComponentPlacement.RELATED, 17, Short.MAX_VALUE)
					.addGroup(gl_settingsPanel.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnStartDelete)
						.addComponent(includeFolders)
						.addComponent(btnListFolders)))
		);
		settingsPanel.setLayout(gl_settingsPanel);
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("");
		selectionListModel = new DefaultListModel<Path>();
		folderTreeModel = new DefaultTreeModel(rootNode);
		
		JTabbedPane mainPane = new JTabbedPane(JTabbedPane.TOP);
		mainPane.setBounds(10, 110, 474, 549);
		contentPane.add(mainPane);
		
		JPanel selectionPanel = new JPanel();
		mainPane.addTab("Selection", null, selectionPanel, null);
		selectionPanel.setLayout(null);
		
		JScrollPane folderScrollPane = new JScrollPane();
		folderScrollPane.setBounds(0, 0, 469, 262);
		selectionPanel.add(folderScrollPane);
		
		foldersTree = new JTree(folderTreeModel);
		folderScrollPane.setViewportView(foldersTree);
		
		JLabel lblFolders = new JLabel("Folders:");
		folderScrollPane.setColumnHeaderView(lblFolders);
		
		btnAddSelection = new JButton("Add Selection");
		btnAddSelection.setEnabled(false);
		btnAddSelection.setBounds(354, 267, 105, 23);
		btnAddSelection.addActionListener(this);
		selectionPanel.add(btnAddSelection);
		
		JScrollPane selectionScrollPane = new JScrollPane();
		selectionScrollPane.setBounds(0, 294, 469, 196);
		selectionPanel.add(selectionScrollPane);
		
		selectionList = new JList<Path>(selectionListModel);
		selectionScrollPane.setViewportView(selectionList);
		
		JLabel lblSelections = new JLabel("Selections:");
		selectionScrollPane.setColumnHeaderView(lblSelections);
		
		btnRemoveSelection = new JButton("Remove Selection");
		btnRemoveSelection.setEnabled(false);
		btnRemoveSelection.setBounds(342, 493, 117, 23);
		btnRemoveSelection.addActionListener(this);
		selectionPanel.add(btnRemoveSelection);
		
		JTabbedPane outputPane = new JTabbedPane(JTabbedPane.TOP);
		mainPane.addTab("Output", null, outputPane, null);
		
		JScrollPane consoleScrollPane = new JScrollPane();
		consoleScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		consoleScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		outputPane.addTab("Console", null, consoleScrollPane, null);
		
		consoleOutput = new JTextPane();
		consoleOutput.setEditable(false);
		consoleScrollPane.setViewportView(consoleOutput);
		
		JSplitPane outputSplitPane = new JSplitPane();
		outputPane.addTab("Threads", null, outputSplitPane, null);
		outputSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		outputSplitPane.setDividerLocation(240);
		
		JScrollPane thread1ScrollPane = new JScrollPane();
		thread1ScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		thread1ScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		outputSplitPane.setLeftComponent(thread1ScrollPane);
		
		thread1Output = new JTextArea();
		thread1Output.setEditable(false);
		thread1ScrollPane.setViewportView(thread1Output);
		
		JLabel thread1Lbl = new JLabel("Thread 1:");
		thread1ScrollPane.setColumnHeaderView(thread1Lbl);
		
		JScrollPane thread2ScrollPane = new JScrollPane();
		thread2ScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		thread2ScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		outputSplitPane.setRightComponent(thread2ScrollPane);
		
		thread2Output = new JTextArea();
		thread2Output.setEditable(false);
		thread2ScrollPane.setViewportView(thread2Output);
		
		JLabel thread2Lbl = new JLabel("Thread 2:");
		thread2ScrollPane.setColumnHeaderView(thread2Lbl);
	}
}