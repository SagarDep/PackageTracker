package info.papdt.express.helper.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.TextInputEditText
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.TooltipCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import cn.nekocode.rxlifecycle.RxLifecycle
import info.papdt.express.helper.R
import info.papdt.express.helper.REQUEST_CODE_CHOOSE_COMPANY
import info.papdt.express.helper.RESULT_EXTRA_COMPANY_CODE
import info.papdt.express.helper.api.Kuaidi100PackageApi
import info.papdt.express.helper.api.RxPackageApi
import info.papdt.express.helper.dao.PackageDatabase
import info.papdt.express.helper.model.BaseMessage
import info.papdt.express.helper.model.Kuaidi100Package
import info.papdt.express.helper.receiver.ConnectivityReceiver
import info.papdt.express.helper.support.PackageApiType
import info.papdt.express.helper.support.SettingsInstance
import info.papdt.express.helper.ui.adapter.HomeToolbarSpinnerAdapter
import info.papdt.express.helper.ui.adapter.NewHomePackageListAdapter
import info.papdt.express.helper.ui.common.AbsActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import moe.feng.common.stepperview.VerticalStepperItemView
import moe.feng.kotlinyan.common.*

class HomeActivity : AbsActivity() {

    companion object {

        const val STATE_ADD_PACKAGE_VIEWS = "state_add_package_views"
        const val STATE_ADD_PACKAGE_NUMBER = "$STATE_ADD_PACKAGE_VIEWS.number"
        const val STATE_ADD_PACKAGE_COMPANY = "$STATE_ADD_PACKAGE_VIEWS.company"
        const val STATE_ADD_PACKAGE_COMPANY_STATE = "$STATE_ADD_PACKAGE_VIEWS.company_state"
        const val STATE_ADD_PACKAGE_NAME = "$STATE_ADD_PACKAGE_VIEWS.name"

    }

    private val coordinatorLayout by lazy<CoordinatorLayout> { findViewById(R.id.coordinator_layout) }
    private val appBarLayout by lazy<AppBarLayout> { findViewById(R.id.app_bar_layout) }
    private val listView by lazy<RecyclerView> { findViewById(android.R.id.list) }
    private val spinner by lazy<Spinner> { mToolbar!!.findViewById(R.id.spinner) }
    private val addButton by lazy<View> { findViewById(R.id.add_button) }
    private val scanButton by lazy<View> { findViewById(R.id.scan_button) }
    private val moreButton by lazy<View> { findViewById(R.id.more_button) }
    private val bottomSheet by lazy<View> { findViewById(R.id.bottom_sheet_add_package) }
    private val bottomSheetBackground by lazy<View> { findViewById(R.id.bottom_sheet_background) }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var addPackageViewHolder: AddPackageViewHolder

    private val listAdapter by lazy {
        NewHomePackageListAdapter()
    }

    private val homeListScrollListener = HomeListScrollListener()

    private val packageDatabase by lazy { PackageDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        if (savedInstanceState == null) {
            bottomSheet.makeGone()
            window.decorView.post {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                bottomSheet.makeVisible()
            }
        } else {

        }

        addPackageViewHolder.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        addPackageViewHolder.onSaveInstanceState(outState)
    }

    override fun setUpViews() {
        listView.addOnScrollListener(homeListScrollListener)

        listView.adapter = listAdapter
        listAdapter.setPackages(packageDatabase.data)

        spinner.adapter = HomeToolbarSpinnerAdapter(this)

        addButton.setOnClickListener {
            bottomSheetBackground.makeVisible()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        TooltipCompat.setTooltipText(scanButton, getString(R.string.activity_scanner))
        scanButton.setOnClickListener { openScanner() }

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.setBottomSheetCallback(AddPackageBottomSheetCallback())

        bottomSheetBackground.setOnTouchListener { _, event ->
            if (event.actionMasked == KeyEvent.ACTION_DOWN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return@setOnTouchListener true
            }
             false
        }

        addPackageViewHolder = AddPackageViewHolder(bottomSheet)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_home, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_search -> {

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ScannerActivity.REQUEST_CODE_SCAN -> {
                if (RESULT_OK == resultCode) {
                    val result = data!![ScannerActivity.EXTRA_RESULT]?.asString()
                    if (result == null || result.isBlank()) {
                        // FIXME Show toast now
                    } else {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        addPackageViewHolder.setNumber(result)
                    }
                }
            }
            REQUEST_CODE_CHOOSE_COMPANY -> {
                if (RESULT_OK == resultCode) {
                    intent!![RESULT_EXTRA_COMPANY_CODE]
                            ?.asString()
                            ?.let(addPackageViewHolder::setCompany)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }
        super.onBackPressed()
    }

    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivityForResult(intent, ScannerActivity.REQUEST_CODE_SCAN)
    }

    private fun onPackageAdd(data: Kuaidi100Package) {
        // TODO Make animated
        listAdapter.setPackages(packageDatabase.data)
        listAdapter.notifyDataSetChanged()

        listView.post {
            listAdapter.items.indexOf(data).takeIf { it != -1 }?.let {
                listView.smoothScrollToPosition(it)
            }
        }
    }

    private inner class AddPackageBottomSheetCallback : BottomSheetBehavior.BottomSheetCallback() {

        override fun onSlide(v: View, slideOffset: Float) {
            bottomSheetBackground.alpha = if (slideOffset.isNaN()) {
                0.5f
            } else {
                Math.min(0.5f, Math.max(0.5f + slideOffset, 0f))
            }
        }

        override fun onStateChanged(v: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_HIDDEN -> {
                    if (bottomSheetBackground.visibility != View.GONE) {
                        bottomSheetBackground.makeGone()
                    }
                    bottomSheet.hideKeyboard()
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
                else -> {
                    if (bottomSheetBackground.visibility != View.VISIBLE) {
                        bottomSheetBackground.makeVisible()
                    }
                }
            }
        }

    }

    private inner class HomeListScrollListener : RecyclerView.OnScrollListener() {

        private val statedElevation by lazy {
            mapOf(true to resources.getDimension(R.dimen.app_bar_elevation), false to 0f)
        }

        private val duration by lazy {
            resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        }

        private var elevationAnimator: Animator? = null
        private var animatorDirection: Boolean? = null

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val layoutManager = recyclerView.layoutManager!! as LinearLayoutManager
            val shouldLift = layoutManager.findFirstCompletelyVisibleItemPosition() != 0

            if (animatorDirection != shouldLift) {
                if (elevationAnimator?.isRunning == true) {
                    elevationAnimator?.cancel()
                }
            }
            animatorDirection = shouldLift
            if (elevationAnimator?.isRunning != true
                    && appBarLayout.elevation != statedElevation[shouldLift]!!) {
                elevationAnimator = ObjectAnimator.ofFloat(
                        appBarLayout,
                        "elevation",
                        statedElevation[!shouldLift]!!,
                        statedElevation[shouldLift]!!
                )
                elevationAnimator!!.duration = duration
                elevationAnimator!!.start()
            }
        }

    }

    private inner class AddPackageViewHolder(val view: View) {

        private val step0: VerticalStepperItemView = view.findViewById(R.id.stepper_input_num)
        private val step1: VerticalStepperItemView = view.findViewById(R.id.stepper_choose_company)
        private val step2: VerticalStepperItemView = view.findViewById(R.id.stepper_find_package)

        private val numberEdit: TextInputEditText = view.findViewById(R.id.number_edit)
        private val step0NextButton: Button = view.findViewById(R.id.step_0_next_button)

        private val currentCompanyText: TextView = view.findViewById(R.id.tv_current_company)
        private val step1NextButton: Button = view.findViewById(R.id.choose_company_next_btn)
        private val detectingLayout: View = view.findViewById(R.id.detecting_layout)
        private val detectErrorView: View = view.findViewById(R.id.error_text)
        private val detectTryAgainButton: Button = view.findViewById(R.id.stepper_try_again)
        private val selectLayout: View = view.findViewById(R.id.select_layout)

        private val addErrorView: View = view.findViewById(R.id.error_layout_add)
        private val addErrorMsg: TextView = view.findViewById(R.id.add_error_message_text)
        private val addErrorDesc: TextView = view.findViewById(R.id.add_error_desc_text)
        private val addLoadingView: View = view.findViewById(R.id.loading_layout_add)
        private val addFinishLayout: View = view.findViewById(R.id.set_name_layout)
        private val nameEdit: TextInputEditText = view.findViewById(R.id.name_edit)

        private var currentStep = 0

        private var companyCode: String = ""

        private var result: Kuaidi100Package? = null

        private var number: String = ""

        private val activity: HomeActivity get() = this@HomeActivity

        init {
            VerticalStepperItemView.bindSteppers(step0, step1, step2)

            step0.isAnimationEnabled = false
            step1.isAnimationEnabled = false
            step2.isAnimationEnabled = false

            step0.summary = resources.string[R.string.stepper_detect_company_summary].format(number)
            step1.summary = when (SettingsInstance.packageApiTypeInt) {
                PackageApiType.BAIDU -> resources.string[R.string.summary_baidu_not_support_choose_company]
                else -> null
            }

            // Set up view in step0
            numberEdit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {

                }

                override fun beforeTextChanged(s: CharSequence?,
                                               start: Int, count: Int, after: Int) {

                }

                override fun onTextChanged(s: CharSequence?,
                                           start: Int, before: Int, count: Int) {
                    step0NextButton.isEnabled = !s.isNullOrBlank()
                    number = s?.toString() ?: ""
                }
            })
            step0NextButton.isEnabled = !numberEdit.text.isNullOrBlank()
            step0NextButton.setOnClickListener {
                number = numberEdit.text.toString()
                currentStep = 1
                step0.nextStep()
                doStep()
            }

            // Set up views in step1
            step1NextButton.setOnClickListener { step1.nextStep() }
            view.findViewById<Button>(R.id.choose_company_change_btn).setOnClickListener {
                val intent = Intent(activity, CompanyChooserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivityForResult(intent, REQUEST_CODE_CHOOSE_COMPANY)
            }
            detectTryAgainButton.setOnClickListener { doStep() }
            view.findViewById<Button>(R.id.choose_company_next_btn).setOnClickListener {
                currentStep = 2
                step1.nextStep()
                doStep()
            }
            view.findViewById<Button>(R.id.back_button_step_1).setOnClickListener {
                currentStep = 0
                step1.prevStep()
                doStep()
            }
            view.findViewById<Button>(R.id.back_button_step_1_2).setOnClickListener {
                currentStep = 0
                step1.prevStep()
                doStep()
            }

            // Set up views in step 2
            view.findViewById<Button>(R.id.try_again_btn_step_2).setOnClickListener { doStep() }
            view.findViewById<Button>(R.id.back_button_step_2).setOnClickListener {
                if (SettingsInstance.packageApiTypeInt == PackageApiType.BAIDU) {
                    currentStep = 0
                    step1.prevStep()
                } else {
                    currentStep = 1
                    step2.prevStep()
                }
                doStep()
            }
            view.findViewById<Button>(R.id.back_button_step_2_2).setOnClickListener {
                if (SettingsInstance.packageApiTypeInt == PackageApiType.BAIDU) {
                    currentStep = 0
                    step1.prevStep()
                } else {
                    currentStep = 1
                    step2.prevStep()
                }
                doStep()
            }
            view.findViewById<Button>(R.id.stepper_add_button).setOnClickListener {
                result?.name = if (nameEdit.text!!.isNotBlank())
                    nameEdit.text.toString() else String.format(getString(R.string.package_name_unnamed),
                        if (number.length >= 4) number.substring(0, 4) else number)
                Observable.just(result!!)
                        .compose(RxLifecycle.bind(activity).withObservable())
                        .map { newData ->
                            val db = PackageDatabase.getInstance(activity)
                            db.add(newData)
                            db.save()
                        }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe { _ ->
                            detectErrorView.makeGone()
                            detectingLayout.makeVisible()
                        }
                        .subscribe { _ ->
                            onPackageAdd(result!!)
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                            setNumber("")
                            companyCode = ""
                            nameEdit.text = null
                            currentStep = 0
                            step0.state = VerticalStepperItemView.STATE_SELECTED
                            step1.state = VerticalStepperItemView.STATE_NORMAL
                            step2.state = VerticalStepperItemView.STATE_NORMAL
                            doStep()
                        }
            }

            doStep()
        }

        private fun doStep() {
            when (currentStep) {
                0 -> {
                    // Clear last company result
                    companyCode = ""
                }
                1 -> {
                    step0.summary = resources.string[R.string.stepper_detect_company_summary]
                            .format(number)
                    if (!companyCode.isEmpty()) {
                        setCompany(companyCode)
                        detectErrorView.makeGone()
                        detectTryAgainButton.makeGone()
                        detectingLayout.makeGone()
                        selectLayout.makeVisible()
                    } else {
                        // Request a detection
                        if (ConnectivityReceiver.readNetworkState(activity)) {
                            RxPackageApi.detectCompany(number, activity)
                                    .doOnSubscribe {
                                        detectErrorView.makeGone()
                                        detectTryAgainButton.makeGone()
                                        detectingLayout.makeVisible()
                                        selectLayout.makeGone()
                                    }
                                    .subscribe({
                                        step1.setErrorText(0)
                                        when (SettingsInstance.packageApiTypeInt) {
                                            PackageApiType.BAIDU -> {
                                                currentStep = 2
                                                step0.state = VerticalStepperItemView.STATE_DONE
                                                step1.nextStep()
                                                doStep()
                                            }
                                            else -> {
                                                detectErrorView.makeGone()
                                                detectTryAgainButton.makeGone()
                                                detectingLayout.makeGone()
                                                selectLayout.makeVisible()
                                            }
                                        }
                                        it?.let(this::setCompany)
                                    }, { err ->
                                        err.printStackTrace()
                                        detectErrorView.makeVisible()
                                        detectTryAgainButton.makeVisible()
                                        detectingLayout.makeGone()
                                        selectLayout.makeGone()
                                    })
                        } else {
                            step1.setErrorText(R.string.message_no_internet_connection)
                            detectErrorView.makeVisible()
                            detectTryAgainButton.makeVisible()
                            detectingLayout.makeGone()
                            selectLayout.makeGone()
                        }
                    }
                }
                2 -> {
                    if (ConnectivityReceiver.readNetworkState(activity)) {
                        RxPackageApi.getPackage(number, companyCode, activity)
                                .doOnSubscribe {
                                    addLoadingView.makeVisible()
                                    addErrorView.makeGone()
                                    addFinishLayout.makeGone()
                                    step2.setErrorText(0)
                                }
                                .subscribe {
                                    if (it.code == BaseMessage.CODE_OKAY && it.data?.getState() != Kuaidi100Package.STATUS_FAILED) {
                                        addErrorView.makeGone()
                                        addLoadingView.makeGone()
                                        addFinishLayout.makeVisible()
                                        result = it.data
                                        view.findViewById<TextView>(R.id.add_message_text).text =
                                                resources.string[R.string.message_successful_format]
                                                        .format(number, currentCompanyText.text)
                                    } else {
                                        addErrorView.makeVisible()
                                        addLoadingView.makeGone()
                                        addFinishLayout.makeGone()
                                        addErrorMsg.setText(R.string.message_no_found)
                                        addErrorDesc.setText(R.string.description_no_found)
                                        step2.setErrorText(R.string.message_no_found)
                                    }
                                }
                    } else {
                        addLoadingView.makeGone()
                        addErrorView.makeVisible()
                        addFinishLayout.makeGone()
                        addErrorMsg.setText(R.string.message_no_internet_connection)
                        addErrorDesc.setText(R.string.description_no_internet_connection)
                        step2.setErrorText(R.string.message_no_internet_connection)
                    }
                }
            }
        }

        internal fun setNumber(number: String) {
            this.number = number
            numberEdit.setText(number)
            step0NextButton.isEnabled = !number.isBlank()
        }

        internal fun setCompany(company: String) {
            this.companyCode = company
            currentCompanyText.text = if (company.isNotBlank())
                Kuaidi100PackageApi.CompanyInfo.getNameByCode(company) else resources.string[R.string.stepper_company_cannot_detect]
            step1NextButton.isEnabled = company.isNotBlank()
            step1.setErrorText(if (company.isBlank()) R.string.stepper_company_cannot_detect else 0)
        }

        fun onSaveInstanceState(outState: Bundle) {
            outState[STATE_ADD_PACKAGE_NUMBER] = number
            outState[STATE_ADD_PACKAGE_COMPANY] = companyCode
            outState.putString(STATE_ADD_PACKAGE_NAME, nameEdit.text?.toString())
        }

        fun onRestoreInstanceState(savedInstanceState: Bundle?) {
            if (savedInstanceState != null) {
                setNumber(savedInstanceState.getString(STATE_ADD_PACKAGE_NUMBER) ?: "")
                setCompany(savedInstanceState.getString(STATE_ADD_PACKAGE_COMPANY) ?: "")
                nameEdit.setText(savedInstanceState.getString(STATE_ADD_PACKAGE_NAME))
            }
        }

    }

}