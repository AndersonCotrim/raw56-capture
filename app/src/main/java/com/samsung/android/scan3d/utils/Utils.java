package com.samsung.android.scan3d.utils;

import android.graphics.Rect;
import android.util.Size;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Utils {

    public static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    public static class WaitLock {

        private final ReentrantLock lock;

        private final Condition condition;

        private int seconds;

        public WaitLock(int seconds) {
            this.seconds = seconds;
            this.lock =  new ReentrantLock();
            this.condition =  lock.newCondition();
        }

        public void notifySignal(){
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }

        public void waitSignal() throws InterruptedException {
            lock.lock();
            try {
                if (!condition.await(seconds, TimeUnit.SECONDS)) {
                    throw new InterruptedException("Timeout");
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public static int countOccurrences(int[][] matrix, int value){

        int count = 0;

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                // Se o número atual é igual ao número para contar, incremente o contador
                if (matrix[i][j] == value) {
                    count++;
                }
            }
        }

        return count;
    }

    public static <T> Set<T> convertToSet(T[] values)  {
        return new HashSet<>(Arrays.asList(values));
    }

    public static String[] getIndexedValues(String[] array, int[] indexes) {
        String[] result =  new String[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            result[i] = array[indexes[i]];
        }
        return result;
    }

    public static Rect calculateZoomRegion(Rect sensorArraySize, float zoomLevel, int deltaXCenter, int deltaYCenter) {
        int centerX = (sensorArraySize.width() / 2)+deltaXCenter;
        int centerY = (sensorArraySize.height() / 2)+deltaYCenter;
        int deltaX = (int)(0.5f * sensorArraySize.width() / zoomLevel);
        int deltaY = (int)(0.5f * sensorArraySize.height() / zoomLevel);
        return new Rect(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY);
    }


}
