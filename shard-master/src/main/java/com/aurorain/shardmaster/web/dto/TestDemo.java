package com.aurorain.shardmaster.web.dto;

public class TestDemo {

    public void start() {
        new Thread(this::test01).start();
        new Thread(this::test02).start();

        while(true) {
            System.out.println(88888);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void test01() {
        while(true) {
            System.out.println(66666);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void test02() {
        while(true) {
            System.out.println(77777);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
