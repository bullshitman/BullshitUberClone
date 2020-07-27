package com.bullshitman.bullshituberclone
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bullshitman.bullshituberclone.utils.UserUtils
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

private const val PICK_IMAGE_REQUEST = 7777

class DriverHomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var imageAvatar: ImageView
    private lateinit var waitingDialog: AlertDialog
    private lateinit var storageReference: StorageReference
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_home)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_home), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.driver_home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun init() {
        storageReference = FirebaseStorage.getInstance().getReference()
        waitingDialog = AlertDialog.Builder(this)
            .setMessage("Waiting..")
            .setCancelable(false).create()
        navView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_sign_out) {
                val builder = AlertDialog.Builder(this@DriverHomeActivity)
                with(builder) {
                    setTitle("Sign out")
                    setMessage("Do you really want to sign out?")
                    setNegativeButton("Cancel") { dialogInterface, _ ->  dialogInterface.dismiss()}
                    setPositiveButton("Sign out") { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this@DriverHomeActivity, SplashScreenActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }.setCancelable(false)
                }
                val dialog = builder.create()
                dialog.setOnShowListener {
                    with(dialog) {
                        getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                        getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setTextColor(ContextCompat.getColor(context, R.color.colorAccent))
                    }
                }
                dialog.show()
            }
            true
        }
        val headerView = navView.getHeaderView(0)
        val textName = headerView.findViewById<View>(R.id.txt_name) as TextView
        val textPhone = headerView.findViewById<View>(R.id.txt_phone) as TextView
        val textStar = headerView.findViewById<View>(R.id.txt_star) as TextView
        imageAvatar = headerView.findViewById<View>(R.id.image_avatar) as ImageView
        textName.text = Common.buildWelcomeMessage()
        textPhone.text = Common.currentUser!!.phoneNumber
        textStar.text = Common.currentUser!!.rating.toString()

        if (Common.currentUser != null && Common.currentUser!!.imageAvatar != null && !Common.currentUser!!.imageAvatar.isBlank()) {
            Glide.with(this)
                .load(Common.currentUser!!.imageAvatar)
                .into(imageAvatar)
        }

        imageAvatar.setOnClickListener {
            val intent = Intent()
            with(intent) {
                setType("image/*")
                setAction(Intent.ACTION_GET_CONTENT)
            }
            startActivityForResult(Intent.createChooser(intent, "Select a picture"), PICK_IMAGE_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                imageUri = data.data
                imageAvatar.setImageURI(imageUri)
                showUploadDialog()
            }
        }
    }

    private fun showUploadDialog() {
        val builder = AlertDialog.Builder(this@DriverHomeActivity)
        with(builder) {
            setTitle("Change avatar")
            setMessage("Do you really want to change avatar?")
            setNegativeButton("Cancel") { dialogInterface, _ ->  dialogInterface.dismiss()}
            setPositiveButton("OK") { _, _ ->
                if (imageUri != null) {
                    waitingDialog.show()
                    val avatarFolder = storageReference.child("avatars/${FirebaseAuth.getInstance().currentUser?.uid}")
                    avatarFolder.putFile(imageUri!!)
                        .addOnFailureListener { e ->
                            Snackbar.make(drawerLayout, e.message!!, Snackbar.LENGTH_LONG).show()
                        }
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                avatarFolder.downloadUrl.addOnSuccessListener { uri ->
                                    val update_data = HashMap<String, Any>()
                                    update_data.put("imageAvatar", uri.toString())
                                    UserUtils.updateUserInfo(drawerLayout, update_data)
                                }
                            }
                            waitingDialog.dismiss()
                        }
                        .addOnProgressListener { taskSnapshot ->
                            val progress = 100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount
                            waitingDialog.setMessage("Uploading: $progress%")
                        }
                }
            }.setCancelable(false)
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            with(dialog) {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this@DriverHomeActivity, android.R.color.holo_red_dark))
                getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this@DriverHomeActivity, R.color.colorAccent))
            }
        }
        dialog.show()
    }
}