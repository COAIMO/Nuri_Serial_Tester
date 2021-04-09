package com.example.nuri_serial_tester

import android.util.Log
import java.nio.ByteBuffer


class  SerialProtocol {

    var mData: ByteArray = ByteArray(100)
    var gData: ByteArray = ByteArray(93)


     fun BuildProtocol(cid: Byte, mode: Byte, data: Byte? = null): ByteArray {
        var ret: ByteArray
        if (data == null) {
            ret = ByteArray(100)
            ret[3] = 2
        } else {

            ret = ByteArray(100)
            ret[3] = 3
            ret[6] = data
//            for (i in 7..99){
//
//            }
        }
        ret[0] = 0xff.toByte()
        ret[1] = 0xfe.toByte()
        ret[2] = cid
        ret[5] = mode

        ret[4] = checkSum(cid, mode, data)
        return ret
    }

    fun BuzzerOn(cid: Byte): ByteArray? {
        return BuildProtocol(cid, 0xa2.toByte(), 0x01)
    }

    fun checkSum(cid: Byte, mode: Byte, data: Byte? = null): Byte {
        if (data == null)
            return ((cid + 2 + mode) % 256).toByte()
        else
            return ((cid + 3 + mode + data) % 256).toByte()

    }

    fun checkSumRecive(data: ByteArray): Byte {
        val sumvalue = (data.sum() - 0xff - 0xfe - data[4])

        return (sumvalue % 256).toByte()
    }


    var modeIDXs = intArrayOf(5, 8, 11, 14, 17)
    var modes = byteArrayOf(1, 2)
    fun Checkmode(data: ByteArray): Boolean {
        var ret:Boolean = true
        modeIDXs.forEach {i -> modeIDXs
            if (!modes.contains(data[i])){
                ret = false
                return@forEach
            }

        }
        return ret
    }


    fun checkHeader(): Boolean {
        return (mData[0] == 0xff.toByte()
                && mData[1] == 0xfe.toByte())
    }

    fun checkDataSize(data: ByteArray): Boolean {
        return (mData.size == data.size)
    }

    fun checkGarbageData(): Boolean {
        val temp = mData.sliceArray(7..99)
        return ( gData.contentEquals(temp))
    }

    fun parse(data: ByteArray):Boolean{
        // 데이터 사이즈 체크
        if (checkDataSize(data)){
            // 파싱 데이터 적제
            data.copyInto(mData)
            // 사이즈 헤더 비교 호출
            if (checkHeader()){
                // 체크섬 비교 체크
                if (mData[4] == checkSumRecive(data)){
                    //가비지 데이터 비교
                    if (checkGarbageData()){
                        // 결과 응답
                    }
                }
            }
        }else{
            return false
        }

        return true
    }


}
