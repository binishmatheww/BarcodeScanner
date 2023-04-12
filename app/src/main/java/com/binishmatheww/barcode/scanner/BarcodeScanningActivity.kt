/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.binishmatheww.barcode.scanner

//import com.google.common.base.Objects
import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.binishmatheww.barcode.scanner.barcode.BarcodeProcessor
import com.binishmatheww.barcode.scanner.camera.CameraSource
import com.binishmatheww.barcode.scanner.camera.CameraSourcePreview
import com.binishmatheww.barcode.scanner.camera.GraphicOverlay
import com.binishmatheww.barcode.scanner.camera.WorkflowModel
import com.binishmatheww.barcode.scanner.camera.WorkflowModel.WorkflowState
import com.google.android.gms.common.internal.Objects
import com.google.android.material.chip.Chip
import java.io.IOException


class BarcodeScanningActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var retakeButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var currentWorkflowState: WorkflowState? = null

    private val workflowModel by viewModels<WorkflowModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_barcode)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@BarcodeScanningActivity)
            cameraSource = CameraSource(this)
        }

        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator = (AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(promptChip)
            }

        retakeButton = findViewById<View>(R.id.retakeButton).apply {
            setOnClickListener(this@BarcodeScanningActivity)
        }

        flashButton = findViewById<View>(R.id.flashButton).apply {
            setOnClickListener(this@BarcodeScanningActivity)
        }

        setUpWorkflowModel()

    }

    override fun onResume() {
        super.onResume()

        if ( checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ){

            retakeButton?.visibility = View.GONE
            workflowModel.markCameraFrozen()
            currentWorkflowState = WorkflowState.NOT_STARTED
            graphicOverlay?.let { gOverlay ->
                cameraSource?.setFrameProcessor(BarcodeProcessor(gOverlay, workflowModel))
            }
            workflowModel.setWorkflowState(WorkflowState.DETECTING)

        }
        else {

            requestPermissions(arrayOf(Manifest.permission.CAMERA),666)

        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if ( checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ){

            retakeButton?.visibility = View.GONE
            workflowModel.markCameraFrozen()
            currentWorkflowState = WorkflowState.NOT_STARTED
            graphicOverlay?.let { gOverlay ->
                cameraSource?.setFrameProcessor(BarcodeProcessor(gOverlay, workflowModel))
            }
            workflowModel.setWorkflowState(WorkflowState.DETECTING)

        }

    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.retakeButton -> {
                stopCameraPreview()
                startCameraPreview()
                retakeButton?.visibility = View.GONE
                promptChip?.visibility = View.GONE
            }
            R.id.flashButton -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
        }
    }

    private fun startCameraPreview() {
        val workflowModel = this.workflowModel
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
                flashButton?.isEnabled = true
            } catch (e: IOException) {
                Log.e(BarcodeScanningActivity::class.java.name, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.workflowModel
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
            flashButton?.isEnabled = false
        }
    }

    private fun setUpWorkflowModel() {

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel.workflowState.observe(this, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(BarcodeScanningActivity::class.java.name, "Current workflow state: ${currentWorkflowState?.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE

            when (workflowState) {
                WorkflowState.DETECTING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.text = "Point at the bar/QR code"
                    startCameraPreview()
                    retakeButton?.visibility = View.GONE
                }
                WorkflowState.UNCLEAR -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.text = "Move the camera closer"
                    startCameraPreview()
                    retakeButton?.visibility = View.GONE
                }
                WorkflowState.PROCESSING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.text = "Processing"
                    stopCameraPreview()
                    retakeButton?.visibility = View.GONE
                }
                WorkflowState.DETECTED, WorkflowState.PROCESSED -> {
                    promptChip?.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> promptChip?.visibility = View.GONE
            }

            val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        workflowModel.detectedBarcode.observe(this) { barcode ->

            barcode?.rawValue?.apply {
                promptChip?.visibility = View.VISIBLE
                promptChip?.text = this
            }
            retakeButton?.visibility = View.VISIBLE

        }

    }

}
