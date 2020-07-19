package com.bullshitman.bullshituberclone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bullshitman.bullshituberclone.Model.DriverInfoModel
import com.bullshitman.bullshituberclone.utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splash_screen.*
import java.util.concurrent.TimeUnit

private const val LOGIN_REQUEST_CODE = 7070
private const val TAG = "SplashScreen"

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var dataBase: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    override fun onStop() {
        super.onStop()
        if (firebaseAuth != null && listener != null) {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    private fun delaySplashScreen() {
        Completable.timer(3,TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe({
                Log.d(TAG,"Splash screen working good.")
                firebaseAuth.addAuthStateListener(listener)
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        init()
    }

    private fun init() {
        dataBase = FirebaseDatabase.getInstance()
        driverInfoRef = dataBase.getReference(Common.DRIVER_INFO_REFERENCE)
        providers = listOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFireBaseAuth ->
            val user = myFireBaseAuth.currentUser
            if (user != null) {
                Toast.makeText(this@SplashScreenActivity, "Welcome back:  ${user.uid}", Toast.LENGTH_SHORT).show()
                FirebaseInstanceId.getInstance()
                    .instanceId
                    .addOnFailureListener { exception ->
                        Log.d("TOKEN", exception.message)
                        Toast.makeText(this@SplashScreenActivity, exception.message, Toast.LENGTH_LONG).show()
                    }
                    .addOnSuccessListener { instanceIdResult ->
                        Log.d("TOKEN", instanceIdResult.token)
                        UserUtils.updateToken(this@SplashScreenActivity, instanceIdResult.token)
                    }
                checkUserFromFireBase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun checkUserFromFireBase() {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, p0.message, Toast.LENGTH_SHORT).show()
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        Toast.makeText(this@SplashScreenActivity, "User already registered!", Toast.LENGTH_SHORT).show()
                        val model = dataSnapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }

            })
    }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)
        val firstName = itemView.findViewById<View>(R.id.edit_first_name) as TextInputEditText
        val lastName = itemView.findViewById<View>(R.id.edit_last_name) as TextInputEditText
        val phoneNumber = itemView.findViewById<View>(R.id.edit_phone_number) as TextInputEditText
        val continueBtn = itemView.findViewById<View>(R.id.btn_continue) as Button

        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null && TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)) {
            phoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        }
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()
        continueBtn.setOnClickListener {
            when {
                TextUtils.isDigitsOnly(firstName.text.toString()) -> {
                    Toast.makeText(this@SplashScreenActivity, "Please, enter First Name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                TextUtils.isDigitsOnly(lastName.text.toString()) -> {
                    Toast.makeText(this@SplashScreenActivity, "Please, enter Last Name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                TextUtils.isDigitsOnly(phoneNumber.text.toString()) -> {
                    Toast.makeText(this@SplashScreenActivity, "Please, enter Phone Number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                else -> {
                    val model = DriverInfoModel()
                    model.firstName = firstName.text.toString()
                    model.lastName = lastName.text.toString()
                    model.phoneNumber = phoneNumber.text.toString()

                    driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                        .setValue(model)
                        .addOnFailureListener{ e ->
                            Toast.makeText(this@SplashScreenActivity, "${e.message}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            progress_bar.visibility = View.GONE
                        }
                        .addOnSuccessListener {
                            Toast.makeText(this@SplashScreenActivity, "Registered!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            goToHomeActivity(model)
                            progress_bar.visibility = View.GONE
                        }
                }
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build(), LOGIN_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(this@SplashScreenActivity, "${response!!.error!!.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {

    }
}