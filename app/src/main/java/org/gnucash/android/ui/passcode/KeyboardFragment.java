/*
 * Copyright (c) 2014 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.passcode;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.gnucash.android.R;

/**
 * Soft numeric keyboard for lock screen and passcode preference.
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class KeyboardFragment extends Fragment {

    private static final int DELAY = 500;

    private TextView pass1;
    private TextView pass2;
    private TextView pass3;
    private TextView pass4;

    private int length = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_numeric_keyboard, container, false);

        pass1 = (TextView) rootView.findViewById(R.id.passcode1);
        pass2 = (TextView) rootView.findViewById(R.id.passcode2);
        pass3 = (TextView) rootView.findViewById(R.id.passcode3);
        pass4 = (TextView) rootView.findViewById(R.id.passcode4);

        rootView.findViewById(R.id.one_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("1");
            }
        });
        rootView.findViewById(R.id.two_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("2");
            }
        });
        rootView.findViewById(R.id.three_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("3");
            }
        });
        rootView.findViewById(R.id.four_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("4");
            }
        });
        rootView.findViewById(R.id.five_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("5");
            }
        });
        rootView.findViewById(R.id.six_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("6");
            }
        });
        rootView.findViewById(R.id.seven_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("7");
            }
        });
        rootView.findViewById(R.id.eight_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("8");
            }
        });
        rootView.findViewById(R.id.nine_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("9");
            }
        });
        rootView.findViewById(R.id.zero_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add("0");
            }
        });
        rootView.findViewById(R.id.delete_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (length) {
                    case 1:
                        pass1.setText(null);
                        length--;
                        break;
                    case 2:
                        pass2.setText(null);
                        length--;
                        break;
                    case 3:
                        pass3.setText(null);
                        length--;
                        break;
                    case 4:
                        pass4.setText(null);
                        length--;
                }
            }
        });

        return rootView;
    }

    private void add(String num) {
        length++;
        switch (length) {
            case 1:
                pass1.setText(num);
                break;
            case 2:
                pass2.setText(num);
                break;
            case 3:
                pass3.setText(num);
                break;
            case 4:
                pass4.setText(num);
                final String code = TextUtils.concat(pass1.getText(), pass2.getText(), pass3.getText(), pass4.getText()).toString();
                length = 0;

                pass4.postDelayed(new Runnable() {
                    public void run() {
                        onPasscodeEntered(code);
                        pass1.setText(null);
                        pass2.setText(null);
                        pass3.setText(null);
                        pass4.setText(null);
                    }
                }, DELAY);
        }
    }

    protected void onPasscodeEntered(@NonNull String code) {
    }

    protected void showWrongPassword() {
        Toast.makeText(requireContext(), R.string.toast_wrong_passcode, Toast.LENGTH_SHORT).show();
    }
}
